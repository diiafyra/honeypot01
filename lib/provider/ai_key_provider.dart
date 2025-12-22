import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import '../models/ai_key.dart';

class AIKeyProvider extends ChangeNotifier {
  final FirebaseFirestore _db = FirebaseFirestore.instance;

  bool loading = false;
  List<AIKey> keys = [];

  /// Load toàn bộ AI Keys
  Future<void> loadKeys() async {
    loading = true;
    notifyListeners();

    try {
      final snapshot = await _db
          .collection('ai_keys')
          // .orderBy('createdAt', descending: true)
          .get();

      keys = snapshot.docs
          .map((doc) => AIKey.fromFirestore(doc.id, doc.data()))
          .toList();
    } catch (e) {
      debugPrint('❌ Load AI keys failed: $e');
    }

    loading = false;
    notifyListeners();
  }

  /// Thêm key mới
  Future<void> addKey({
    required String key,
    String? description,
  }) async {
    try {
      await _db.collection('ai_keys').doc(key).set({
        'description': description ?? '',
        'created_at': DateTime.now().millisecondsSinceEpoch, // 🔥 long
      });

      await loadKeys();
    } catch (e) {
      debugPrint('❌ Add AI key failed: $e');
    }
  }

  /// Xóa key
  Future<void> deleteKey(String key) async {
    try {
      await _db.collection('ai_keys').doc(key).delete();
      keys.removeWhere((k) => k.key == key);
      notifyListeners();
    } catch (e) {
      debugPrint('❌ Delete AI key failed: $e');
    }
  }

  Future<void> forceReload() async {
    await loadKeys();
  }
}
