import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/spam_item.dart';

class SpamDetailScreen extends StatelessWidget {
  final DocumentSnapshot<Map<String, dynamic>> doc;
  final SpamItem item;
  const SpamDetailScreen({super.key, required this.doc, required this.item});

  String _formatDate(DateTime d) {
    if (d.millisecondsSinceEpoch == 0) return '—';
    return DateFormat('dd MMM yyyy').format(d);
  }

  @override
  Widget build(BuildContext context) {
    final data = doc.data() ?? {};
    final verification =
        data['verification_status'] ?? item.verificationStatus ?? 'UNKNOWN';
    final callerName =
        data['caller_display_name'] ?? item.callerDisplayName ?? '';
    final callType =
        data['handle_presentation'] ?? item.handlePresentation ?? 'UNKNOWN';
    final callLogsStream = FirebaseFirestore.instance
        .collection('tool_call_logs')
        .where('spam_number', isEqualTo: item.id)
        .snapshots();

    return Scaffold(
      appBar: AppBar(
        title: const Text('CHI TIẾT'),
        backgroundColor: Colors.white,
        foregroundColor: Colors.black87,
        elevation: 0.5,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 1st block: header with number and last call
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.indigo[700],
                borderRadius: BorderRadius.circular(14),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.id,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Call type: $callType',
                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Last call: ${_formatDate(item.lastSeen)}',
                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // 2nd block: Thông tin khác
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'THÔNG TIN KHÁC',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: Colors.black87,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 20),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Flexible(
                        flex: 3,
                        child: Text(
                          'Verification status:',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(flex: 2, child: Text(verification.toString())),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Flexible(
                        flex: 3,
                        child: Text(
                          'Caller display name:',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Flexible(flex: 2, child: Text(callerName.toString())),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 16),

            // 3rd block: Call logs (blank for now)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: Colors.purple.shade50,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'CALL LOGS',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: Colors.black87,
                    ),
                  ),
                  const SizedBox(height: 12),
                  StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
                    stream: callLogsStream,
                    builder: (context, snapshot) {
                      if (snapshot.hasError) {
                        return const Text('Error loading call logs');
                      }
                      if (!snapshot.hasData) {
                        return const CircularProgressIndicator();
                      }
                      final logs = snapshot.data!.docs;
                      if (logs.isEmpty) {
                        return const Text('No call logs available');
                      }
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: logs.map((log) {
                          final data = log.data();
                          final transcript = data['transcript'] ?? '';

                          DateTime? ts;
                          final rawTs =
                              data['call_time'] ??
                              data['timestamp'] ??
                              data['time'] ??
                              data['created_at'];
                          if (rawTs is Timestamp) {
                            ts = rawTs.toDate();
                          } else if (rawTs is int) {
                            ts = DateTime.fromMillisecondsSinceEpoch(rawTs);
                          } else if (rawTs is String) {
                            try {
                              ts = DateTime.parse(rawTs);
                            } catch (_) {
                              ts = null;
                            }
                          }

                          final dynDur =
                              data['duration'] ??
                              data['length'] ??
                              data['duration_seconds'];
                          String durationText = '';
                          if (dynDur != null) {
                            if (dynDur is num) {
                              durationText = '${dynDur}s';
                            } else {
                              durationText = dynDur.toString();
                            }
                          }

                          final timeStr = ts != null
                              ? DateFormat('HH:mm:ss dd MMM yyyy').format(ts)
                              : '';

                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 8.0),
                            child: Container(
                              width: double.infinity,
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: Colors.white,
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(color: Colors.black12),
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (timeStr.isNotEmpty)
                                    Row(
                                      children: [
                                        Text(
                                          timeStr,
                                          style: const TextStyle(
                                            fontSize: 12,
                                            color: Colors.black54,
                                          ),
                                        ),
                                        if (durationText.isNotEmpty) ...[
                                          const SizedBox(width: 8),
                                          Text(
                                            durationText,
                                            style: const TextStyle(
                                              fontSize: 12,
                                              color: Colors.black54,
                                            ),
                                          ),
                                        ],
                                      ],
                                    ),
                                  if (timeStr.isNotEmpty)
                                    const SizedBox(height: 8),
                                  Text(transcript),
                                  const SizedBox(height: 8),
                                  const Divider(
                                    height: 1,
                                    color: Colors.black12,
                                  ),
                                ],
                              ),
                            ),
                          );
                        }).toList(),
                      );
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
