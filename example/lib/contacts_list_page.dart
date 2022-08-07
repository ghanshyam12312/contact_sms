import 'dart:developer';

import 'package:contact_sms/contacts_service.dart';
import 'package:flutter/material.dart';

import 'contact_detail.dart';
import 'main.dart';

class ContactListPage extends StatefulWidget {
  @override
  _ContactListPageState createState() => _ContactListPageState();
}

class _ContactListPageState extends State<ContactListPage> {
  List<Contact>? _contacts;

  @override
  void initState() {
    super.initState();
    refreshContacts();
  }

  Future<void> refreshContacts() async {
    // Load without thumbnails initially.
    var contacts = (await ContactsService.getContacts(
        withThumbnails: false, iOSLocalizedLabels: iOSLocalizedLabels));
//      var contacts = (await ContactsService.getContactsForPhone("8554964652"))
//          ;
    setState(() {
      _contacts = contacts;
    });

    // Lazy load thumbnails after rendering initial contacts.
    for (final contact in contacts) {
      ContactsService.getAvatar(contact).then((avatar) {
        if (avatar == null) return; // Don't redraw if no change.
        setState(() => contact.avatar = avatar);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          'Contacts List',
        ),
      ),
      body: SafeArea(
        child: _contacts != null
            ? ListView.builder(
                itemCount: _contacts?.length ?? 0,
                itemBuilder: (BuildContext context, int index) {
                  Contact? c = _contacts?.elementAt(index);
                  return ListTile(
                    onTap: () {
                      Navigator.of(context).push(MaterialPageRoute(
                          builder: (BuildContext context) =>
                              ContactDetailsPage(contact: c)));
                    },
                    leading: (c?.avatar != null && (c?.avatar?.length ?? 0) > 0)
                        ? CircleAvatar(backgroundImage: MemoryImage(c!.avatar!))
                        : CircleAvatar(child: Text(c!.initials())),
                    title: Text(c.displayName ?? ""),
                  );
                },
              )
            : Center(
                child: CircularProgressIndicator(),
              ),
      ),
    );
  }
}
