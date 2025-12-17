import 'package:cloud_firestore/cloud_firestore.dart';

class SpamItem {
  final String id;
  final String label;
  final double confidence; // 0.0 - 1.0
  final int callCount;
  final DateTime createDate;
  final DateTime lastSeen;
  final int source;
  final String verificationStatus;
  final String handlePresentation;
  final String callerDisplayName;

  SpamItem({
    required this.id,
    required this.label,
    required this.confidence,
    required this.callCount,
    required this.createDate,
    required this.lastSeen,
    required this.source,
    required this.verificationStatus,
    required this.handlePresentation,
    required this.callerDisplayName,
  });

  factory SpamItem.fromDoc(DocumentSnapshot<Map<String, dynamic>> doc) {
    final d = doc.data() ?? {};

    double parseConfidence(dynamic x) {
      if (x == null) return 0.0;
      final s = x.toString();
      final v = double.tryParse(s);
      if (v != null) return v;
      return 0.0;
    }

    int parseInt(dynamic x) {
      if (x == null) return 0;
      if (x is int) return x;
      return int.tryParse(x.toString()) ?? 0;
    }

    DateTime parseDate(dynamic x) {
      if (x == null) return DateTime.fromMillisecondsSinceEpoch(0);
      if (x is int) return DateTime.fromMillisecondsSinceEpoch(x);
      if (x is Timestamp) return x.toDate();
      return DateTime.fromMillisecondsSinceEpoch(
        int.tryParse(x.toString()) ?? 0,
      );
    }

    return SpamItem(
      id: doc.id,
      label: d['label'] ?? 'unknown',
      confidence: parseConfidence(d['confidence']),
      callCount: parseInt(d['call_count']),
      createDate: parseDate(
        d['create_date'] ?? d['createDate'] ?? d['createAt'],
      ),
      lastSeen: parseDate(d['last_seen']),
      source: parseInt(d['source']),
      verificationStatus: d['verification_status'] ?? '',
      handlePresentation: d['handle_presentation'] ?? '',
      callerDisplayName: d['caller_display_name'] ?? '',
    );
  }
}
