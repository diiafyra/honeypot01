import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import '../models/label.dart';

class LabelProvider extends ChangeNotifier {
  final FirebaseFirestore _db = FirebaseFirestore.instance;

  bool loading = false;
  List<Label> labels = [];

  /// Load labels
  Future<void> loadLabels() async {
    loading = true;
    notifyListeners();

    try {
      final snapshot = await _db
          .collection('labels')
          // .orderBy('createdAt', descending: true)
          .get();

      labels = snapshot.docs
          .map((doc) => Label.fromFirestore(doc.id, doc.data()))
          .toList();
    } catch (e) {
      debugPrint('❌ Load labels failed: $e');
    }

    loading = false;
    notifyListeners();
  }

  /// Add label
  Future<void> addLabel({
    required String name,
    String? description,
  }) async {
    try {
      await _db.collection('labels').doc(name).set({
        'description': description ?? '',
        'created_at': DateTime.now().millisecondsSinceEpoch,
        'spam_count': 0,
      });

      await loadLabels();
    } catch (e) {
      debugPrint('❌ Add label failed: $e');
    }
  }

  /// Delete label
  Future<void> deleteLabel(String name) async {
    try {
      await _db.collection('labels').doc(name).delete();
      labels.removeWhere((l) => l.name == name);
      notifyListeners();
    } catch (e) {
      debugPrint('❌ Delete label failed: $e');
    }
  }
}
