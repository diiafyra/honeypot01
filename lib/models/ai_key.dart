class AIKey {
  final String key;
  final String? description;
  final int? createdAt; // epoch millis

  AIKey({
    required this.key,
    this.description,
    this.createdAt,
  });

  factory AIKey.fromFirestore(String id, Map<String, dynamic> data) {
    return AIKey(
      key: id,
      description: data['description'] as String?,
      createdAt: data['created_at'] as int?,
    );
  }
}
