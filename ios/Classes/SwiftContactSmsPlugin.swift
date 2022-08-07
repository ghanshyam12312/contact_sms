import Flutter
import UIKit
import Contacts
import ContactsUI
import MessageUI
import Foundation
import Swift


public class SwiftContactSmsPlugin: NSObject, FlutterPlugin, CNContactViewControllerDelegate, CNContactPickerDelegate {
    private var result: FlutterResult? = nil
    private var localizedLabels: Bool = true
    private let rootViewController: UIViewController
    static let FORM_OPERATION_CANCELED: Int = 1
    static let FORM_COULD_NOT_BE_OPEN: Int = 2

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_contacts", binaryMessenger: registrar.messenger())
        let rootViewController = UIApplication.shared.delegate!.window!!.rootViewController!;
        let instance = SwiftContactSmsPlugin(rootViewController)
        registrar.addMethodCallDelegate(instance, channel: channel)
        instance.preLoadContactView()
    }

    init(_ rootViewController: UIViewController) {
        self.rootViewController = rootViewController
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getContacts":
            let arguments = call.arguments as! [String:Any]
            result(getContacts(query: (arguments["query"] as? String), withThumbnails: arguments["withThumbnails"] as! Bool,
                               photoHighResolution: arguments["photoHighResolution"] as! Bool, phoneQuery:  false, orderByGivenName: arguments["orderByGivenName"] as! Bool,
                               localizedLabels: arguments["iOSLocalizedLabels"] as! Bool ))

        case "openExistingContact":
            let arguments = call.arguments as! [String : Any]
            let contact = arguments["contact"] as! [String : Any]
            localizedLabels = arguments["iOSLocalizedLabels"] as! Bool
            self.result = result
            _ = openExistingContact(contact: contact, result: result)
         case "sendSMS":
               let _arguments = call.arguments as! [String : Any];
              #if targetEnvironment(simulator)
                result(FlutterError(
                    code: "message_not_sent",
                    message: "Cannot send message on this device!",
                    details: "Cannot send SMS and MMS on a Simulator. Test on a real device."
                  )
                )
              #else
                if (MFMessageComposeViewController.canSendText()) {
                  self.result = result
                  let controller = MFMessageComposeViewController()
                  controller.body = _arguments["message"] as? String
                  controller.recipients = _arguments["recipients"] as? [String]
                  controller.messageComposeDelegate = self
                  UIApplication.shared.keyWindow?.rootViewController?.present(controller, animated: true, completion: nil)
                } else {
                  result(FlutterError(
                      code: "device_not_capable",
                      message: "The current device is not capable of sending text messages.",
                      details: "A device may be unable to send messages if it does not support messaging or if it is not currently configured to send messages. This only applies to the ability to send text messages via iMessage, SMS, and MMS."
                    )
                  )
                }
              #endif

            case "canSendSMS":
             #if targetEnvironment(simulator)
                result(false)
              #else
                if (MFMessageComposeViewController.canSendText()) {
                  result(true)
                } else {
                  result(false)
                }
              #endif

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    func getContacts(query : String?, withThumbnails: Bool, photoHighResolution: Bool, phoneQuery: Bool, emailQuery: Bool = false, orderByGivenName: Bool, localizedLabels: Bool) -> [[String:Any]]{

        var contacts : [CNContact] = []
        var result = [[String:Any]]()

        //Create the store, keys & fetch request
        let store = CNContactStore()
        var keys = [CNContactFormatter.descriptorForRequiredKeys(for: .fullName),
                    CNContactEmailAddressesKey,
                    CNContactPhoneNumbersKey,
                    CNContactFamilyNameKey,
                    CNContactGivenNameKey,
                    CNContactMiddleNameKey,] as [Any]

        if(withThumbnails){
            if(photoHighResolution){
                keys.append(CNContactImageDataKey)
            } else {
                keys.append(CNContactThumbnailImageDataKey)
            }
        }

        let fetchRequest = CNContactFetchRequest(keysToFetch: keys as! [CNKeyDescriptor])
        // Set the predicate if there is a query
        if query != nil && !phoneQuery && !emailQuery {
            fetchRequest.predicate = CNContact.predicateForContacts(matchingName: query!)
        }

        if #available(iOS 11, *) {
            if query != nil && phoneQuery {
                let phoneNumberPredicate = CNPhoneNumber(stringValue: query!)
                fetchRequest.predicate = CNContact.predicateForContacts(matching: phoneNumberPredicate)
            } else if query != nil && emailQuery {
                fetchRequest.predicate = CNContact.predicateForContacts(matchingEmailAddress: query!)
            }
        }

        // Fetch contacts
        do{
            try store.enumerateContacts(with: fetchRequest, usingBlock: { (contact, stop) -> Void in

                if phoneQuery {
                    if #available(iOS 11, *) {
                        contacts.append(contact)
                    } else if query != nil && self.has(contact: contact, phone: query!){
                        contacts.append(contact)
                    }
                } else if emailQuery {
                    if #available(iOS 11, *) {
                        contacts.append(contact)
                    } else if query != nil && (contact.emailAddresses.contains { $0.value.caseInsensitiveCompare(query!) == .orderedSame}) {
                        contacts.append(contact)
                    }
                } else {
                    contacts.append(contact)
                }

            })
        }
        catch let error as NSError {
            print(error.localizedDescription)
            return result
        }

        if (orderByGivenName) {
            contacts = contacts.sorted { (contactA, contactB) -> Bool in
                contactA.givenName.lowercased() < contactB.givenName.lowercased()
            }
        }

        // Transform the CNContacts into dictionaries
        for contact : CNContact in contacts{
            result.append(contactToDictionary(contact: contact, localizedLabels: localizedLabels))
        }

        return result
    }

    private func has(contact: CNContact, phone: String) -> Bool {
        if (!contact.phoneNumbers.isEmpty) {
            let phoneNumberToCompareAgainst = phone.components(separatedBy: NSCharacterSet.decimalDigits.inverted).joined(separator: "")
            for phoneNumber in contact.phoneNumbers {

                if let phoneNumberStruct = phoneNumber.value as CNPhoneNumber? {
                    let phoneNumberString = phoneNumberStruct.stringValue
                    let phoneNumberToCompare = phoneNumberString.components(separatedBy: NSCharacterSet.decimalDigits.inverted).joined(separator: "")
                    if phoneNumberToCompare == phoneNumberToCompareAgainst {
                        return true
                    }
                }
            }
        }
        return false
    }


    func preLoadContactView() {
        DispatchQueue.main.asyncAfter(deadline: .now()+5) {
            NSLog("Preloading CNContactViewController")
            let contactViewController = CNContactViewController.init(forNewContact: nil)
        }
    }


    public func contactViewController(_ viewController: CNContactViewController, didCompleteWith contact: CNContact?) {
        viewController.dismiss(animated: true, completion: nil)
        if let result = self.result {
            if let contact = contact {
                result(contactToDictionary(contact: contact, localizedLabels: localizedLabels))
            } else {
                result(SwiftContactSmsPlugin.FORM_OPERATION_CANCELED)
            }
            self.result = nil
        }
    }

    func openExistingContact(contact: [String:Any], result: FlutterResult ) ->  [String:Any]? {
         let store = CNContactStore()
         do {
            // Check to make sure dictionary has an identifier
             guard let identifier = contact["identifier"] as? String else{
                 result(SwiftContactSmsPlugin.FORM_COULD_NOT_BE_OPEN)
                 return nil;
             }
            let backTitle = contact["backTitle"] as? String

             let keysToFetch = [CNContactFormatter.descriptorForRequiredKeys(for: .fullName),
                                CNContactIdentifierKey,
                                CNContactEmailAddressesKey,
                                CNContactImageDataKey,
                                CNContactPhoneNumbersKey,
                                CNContactViewController.descriptorForRequiredKeys()
                ] as! [CNKeyDescriptor]
            let cnContact = try store.unifiedContact(withIdentifier: identifier, keysToFetch: keysToFetch)
            let viewController = CNContactViewController(for: cnContact)

             viewController.delegate = self
            DispatchQueue.main.async {
                let navigation = UINavigationController .init(rootViewController: viewController)
                var currentViewController = UIApplication.shared.keyWindow?.rootViewController
                while let nextView = currentViewController?.presentedViewController {
                    currentViewController = nextView
                }
                let activityIndicatorView = UIActivityIndicatorView.init(style: UIActivityIndicatorView.Style.gray)
                activityIndicatorView.frame = (UIApplication.shared.keyWindow?.frame)!
                activityIndicatorView.startAnimating()
                activityIndicatorView.backgroundColor = UIColor.white
                navigation.view.addSubview(activityIndicatorView)
                currentViewController!.present(navigation, animated: true, completion: nil)

                DispatchQueue.main.asyncAfter(deadline: .now()+0.5 ){
                    activityIndicatorView.removeFromSuperview()
                }
            }
            return nil
         } catch {
            NSLog(error.localizedDescription)
            result(SwiftContactSmsPlugin.FORM_COULD_NOT_BE_OPEN)
            return nil
         }
     }


    func dictionaryToContact(dictionary : [String:Any]) -> CNMutableContact{
        let contact = CNMutableContact()

        //Simple fields
        contact.givenName = dictionary["givenName"] as? String ?? ""
        contact.familyName = dictionary["familyName"] as? String ?? ""
        contact.middleName = dictionary["middleName"] as? String ?? ""

        if let avatarData = (dictionary["avatar"] as? FlutterStandardTypedData)?.data {
            contact.imageData = avatarData
        }

        //Phone numbers
        if let phoneNumbers = dictionary["phones"] as? [[String:String]]{
            for phone in phoneNumbers where phone["value"] != nil {
                contact.phoneNumbers.append(CNLabeledValue(label:getPhoneLabel(label:phone["label"]),value:CNPhoneNumber(stringValue:phone["value"]!)))
            }
        }

        //Emails
        if let emails = dictionary["emails"] as? [[String:String]]{
            for email in emails where nil != email["value"] {
                let emailLabel = email["label"] ?? ""
                contact.emailAddresses.append(CNLabeledValue(label:getCommonLabel(label: emailLabel), value:email["value"]! as NSString))
            }
        }

        return contact
    }

    func contactToDictionary(contact: CNContact, localizedLabels: Bool) -> [String:Any]{

        var result = [String:Any]()

        //Simple fields
        result["identifier"] = contact.identifier
        result["displayName"] = CNContactFormatter.string(from: contact, style: CNContactFormatterStyle.fullName)
        result["givenName"] = contact.givenName
        result["familyName"] = contact.familyName
        result["middleName"] = contact.middleName

        if contact.isKeyAvailable(CNContactThumbnailImageDataKey) {
            if let avatarData = contact.thumbnailImageData {
                result["avatar"] = FlutterStandardTypedData(bytes: avatarData)
            }
        }
        if contact.isKeyAvailable(CNContactImageDataKey) {
            if let avatarData = contact.imageData {
                result["avatar"] = FlutterStandardTypedData(bytes: avatarData)
            }
        }

        //Phone numbers
        var phoneNumbers = [[String:String]]()
        for phone in contact.phoneNumbers{
            var phoneDictionary = [String:String]()
            phoneDictionary["value"] = phone.value.stringValue
            phoneDictionary["label"] = "other"
            if let label = phone.label{
                phoneDictionary["label"] = localizedLabels ? CNLabeledValue<NSString>.localizedString(forLabel: label) : getRawPhoneLabel(label);
            }
            phoneNumbers.append(phoneDictionary)
        }
        result["phones"] = phoneNumbers

        //Emails
        var emailAddresses = [[String:String]]()
        for email in contact.emailAddresses{
            var emailDictionary = [String:String]()
            emailDictionary["value"] = String(email.value)
            emailDictionary["label"] = "other"
            if let label = email.label{
                emailDictionary["label"] = localizedLabels ? CNLabeledValue<NSString>.localizedString(forLabel: label) : getRawCommonLabel(label);
            }
            emailAddresses.append(emailDictionary)
        }
        result["emails"] = emailAddresses


        return result
    }

    func getPhoneLabel(label: String?) -> String{
        let labelValue = label ?? ""
        switch(labelValue){
        case "main": return CNLabelPhoneNumberMain
        case "mobile": return CNLabelPhoneNumberMobile
        case "iPhone": return CNLabelPhoneNumberiPhone
        case "work": return CNLabelWork
        case "home": return CNLabelHome
        case "other": return CNLabelOther
        default: return labelValue
        }
    }

    func getCommonLabel(label:String?) -> String{
        let labelValue = label ?? ""
        switch(labelValue){
        case "work": return CNLabelWork
        case "home": return CNLabelHome
        case "other": return CNLabelOther
        default: return labelValue
        }
    }

    func getRawPhoneLabel(_ label: String?) -> String{
        let labelValue = label ?? ""
        switch(labelValue){
            case CNLabelPhoneNumberMain: return "main"
            case CNLabelPhoneNumberMobile: return "mobile"
            case CNLabelPhoneNumberiPhone: return "iPhone"
            case CNLabelWork: return "work"
            case CNLabelHome: return "home"
            case CNLabelOther: return "other"
            default: return labelValue
        }
    }

    func getRawCommonLabel(_ label: String?) -> String{
        let labelValue = label ?? ""
        switch(labelValue){
            case CNLabelWork: return "work"
            case CNLabelHome: return "home"
            case CNLabelOther: return "other"
            default: return labelValue
        }
    }

     public func messageComposeViewController(_ controller: MFMessageComposeViewController, didFinishWith result: MessageComposeResult) {
        let map: [MessageComposeResult: String] = [
            MessageComposeResult.sent: "sent",
            MessageComposeResult.cancelled: "cancelled",
            MessageComposeResult.failed: "failed",
        ]
        if let callback = self.result {
            callback(map[result])
        }
        UIApplication.shared.keyWindow?.rootViewController?.dismiss(animated: true, completion: nil)
      }

}
