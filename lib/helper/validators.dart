class Validators {
  // Validate API Key
  static String? validateApiKey(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Vui lòng nhập API key';
    }

    final trimmed = value.trim();

    if (trimmed.length < 10) {
      return 'API key quá ngắn (tối thiểu 10 ký tự)';
    }

    if (trimmed.length > 200) {
      return 'API key quá dài (tối đa 200 ký tự)';
    }

    // Check for invalid characters (only allow alphanumeric, dash, underscore)
    if (!RegExp(r'^[a-zA-Z0-9\-_]+$').hasMatch(trimmed)) {
      return 'API key chỉ chứa chữ, số, gạch ngang và gạch dưới';
    }

    return null;
  }

  // Validate Label Name
  static String? validateLabelName(String? value) {
    if (value == null || value.trim().isEmpty) {
      return 'Vui lòng nhập tên phân loại';
    }

    final trimmed = value.trim();

    if (trimmed.length < 2) {
      return 'Tên phân loại quá ngắn (tối thiểu 2 ký tự)';
    }

    if (trimmed.length > 50) {
      return 'Tên phân loại quá dài (tối đa 50 ký tự)';
    }

    return null;
  }

  // Validate Description (optional but if provided, must be valid)
  static String? validateDescription(String? value) {
    if (value == null || value.trim().isEmpty) {
      return null; // Description is optional
    }

    final trimmed = value.trim();

    if (trimmed.length > 200) {
      return 'Mô tả quá dài (tối đa 200 ký tự)';
    }

    return null;
  }
}