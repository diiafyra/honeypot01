class Label {
  final String name;
  final String? description;
  final int? createdAt; // epoch millis
  final int? spamCount;

  Label({
    required this.name,
    this.description,
    this.createdAt,
    this.spamCount,
  });

  factory Label.fromFirestore(String id, Map<String, dynamic> data) {
    return Label(
      name: id,
      description: data['description'] as String?,
      createdAt: data['created_at'] as int?,
      spamCount: data['spam_count'] as int?,
    );
  }
}
