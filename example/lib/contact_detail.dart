import 'dart:developer';

import 'package:contact_sms/contacts_service.dart';
import 'package:contact_sms_example/ui/single_row.dart';
import 'package:flutter/material.dart';

class ContactDetailsPage extends StatefulWidget {
  final Contact? contact;

  @override
  _ContactDetailsPageState createState() => _ContactDetailsPageState();

  const ContactDetailsPage({required this.contact}) : super();
}

class _ContactDetailsPageState extends State<ContactDetailsPage> {
  TextEditingController _textFieldController = TextEditingController();

  void _send() {
    if (_textFieldController.text.isNotEmpty) {
      _sendSMS(_textFieldController.text);
    }
  }

  Future<void> _sendSMS(String recipients) async {
    try {
      String _result = await sendSMS(
        message:
            '${widget.contact?.displayName} ${widget.contact?.middleName} ${widget.contact?.familyName}\n${widget.contact?.phones?.join('\n')}',
        recipients: recipients,
      );
      log('data: $_result');
    } catch (error) {
      log('data: $error');
    }
    Navigator.of(context).pop();
  }

  _displayDialog(BuildContext context, Contact? contact) async {
    return showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: Text('Share with...'),
            content: TextField(
              controller: _textFieldController,
              textInputAction: TextInputAction.go,
              keyboardType: TextInputType.numberWithOptions(),
              decoration: InputDecoration(
                hintText: "Enter recipient number",
              ),
            ),
            actions: <Widget>[
              new TextButton(
                child: new Text('Share'),
                onPressed: () {
                  _send();
                },
              )
            ],
          );
        });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.contact?.displayName ?? ""),
        actions: <Widget>[
          IconButton(
            icon: Icon(Icons.send),
            onPressed: () => _displayDialog(context, widget.contact),
          ),
        ],
      ),
      body: SafeArea(
        child: Column(children: [
          const SizedBox(
            height: 16,
          ),
          (widget.contact?.avatar != null &&
                  (widget.contact?.avatar?.length ?? 0) > 0)
              ? CircleAvatar(
                  minRadius: 30,
                  maxRadius: 40,
                  backgroundImage: MemoryImage(widget.contact!.avatar!))
              : CircleAvatar(
                  minRadius: 30,
                  maxRadius: 40,
                  child: Text(widget.contact?.initials() ?? '')),
          const SizedBox(
            height: 16,
          ),
          Card(
            child: ListTile(
              contentPadding: const EdgeInsets.all(8),
              leading: const Padding(
                padding: const EdgeInsets.only(right: 16),
                child: Icon(
                  Icons.account_circle_outlined,
                  size: 28,
                ),
              ),
              title: Text(widget.contact?.displayName ?? ''),
            ),
          ),
          ContactSingleInfoRow(
            singleInfo: widget.contact?.phones,
            singleIcon: Icons.phone_outlined,
          ),
          ContactSingleInfoRow(
            singleInfo: widget.contact?.emails,
            singleIcon: Icons.email_outlined,
          ),
          const SizedBox(
            height: 28,
          ),
        ]),
      ),
    );
  }
}
