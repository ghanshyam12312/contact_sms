#import "ContactSmsPlugin.h"
#if __has_include(<contact_sms/contact_sms-Swift.h>)
#import <contact_sms/contact_sms-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "contact_sms-Swift.h"
#endif

@implementation ContactSmsPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftContactSmsPlugin registerWithRegistrar:registrar];
}
@end
