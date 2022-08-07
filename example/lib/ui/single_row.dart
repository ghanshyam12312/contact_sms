import 'package:contact_sms/contacts_service.dart';
import 'package:flutter/material.dart';

class ContactSingleInfoRow extends StatelessWidget {
  const ContactSingleInfoRow({required this.singleInfo, this.singleIcon})
      : super();

  final List<Item>? singleInfo;
  final IconData? singleIcon;

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: singleInfo?.length,
      itemBuilder: (context, i) => Card(
        child: ListTile(
          contentPadding: const EdgeInsets.all(8),
          leading: Padding(
            padding: const EdgeInsets.only(right: 16),
            child: Icon(
              singleIcon,
              size: 28,
            ),
          ),
          title: Text(singleInfo?[i].value ?? ''),
          trailing: Text(singleInfo?[i].label ?? ''),
        ),
      ),
    );
  }
}
