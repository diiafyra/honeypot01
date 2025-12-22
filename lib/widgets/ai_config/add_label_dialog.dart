import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../helper/validators.dart';
import '../../provider/label_provider.dart';

class AddLabelDialog extends StatefulWidget {
  const AddLabelDialog({super.key});

  @override
  State<AddLabelDialog> createState() => _AddLabelDialogState();
}

class _AddLabelDialogState extends State<AddLabelDialog> {
  final _formKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _descController = TextEditingController();
  bool _isSubmitting = false;

  @override
  void dispose() {
    _nameController.dispose();
    _descController.dispose();
    super.dispose();
  }

  Future<void> _handleSubmit(BuildContext dialogContext) async {
    // Validate form
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() => _isSubmitting = true);

    // Capture references before async gap
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(dialogContext);
    final provider = context.read<LabelProvider>();

    try {
      await provider.addLabel(
        name: _nameController.text.trim(),
        description: _descController.text.trim(),
      );

      if (!mounted) return;

      // Close dialog
      navigator.pop();

      // Show success message
      messenger.showSnackBar(
        const SnackBar(
          content: Text('Đã thêm phân loại thành công'),
          backgroundColor: Colors.green,
          duration: Duration(seconds: 2),
        ),
      );
    } catch (e) {
      if (!mounted) return;

      setState(() => _isSubmitting = false);

      // Show error message
      messenger.showSnackBar(
        SnackBar(
          content: Text('Lỗi: ${e.toString()}'),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Thêm phân loại'),
      content: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _nameController,
                decoration: const InputDecoration(
                  labelText: 'Tên phân loại *',
                  hintText: 'Ví dụ: Spam, Lừa đảo',
                  prefixIcon: Icon(Icons.label),
                  border: OutlineInputBorder(),
                ),
                validator: Validators.validateLabelName,
                enabled: !_isSubmitting,
                maxLength: 50,
                textInputAction: TextInputAction.next,
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _descController,
                decoration: const InputDecoration(
                  labelText: 'Mô tả',
                  hintText: 'Mô tả chi tiết về phân loại này',
                  prefixIcon: Icon(Icons.description),
                  border: OutlineInputBorder(),
                ),
                validator: Validators.validateDescription,
                enabled: !_isSubmitting,
                maxLength: 200,
                maxLines: 3,
                textInputAction: TextInputAction.done,
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isSubmitting ? null : () => Navigator.of(context).pop(),
          child: const Text('Hủy'),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : () => _handleSubmit(context),
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.blue,
            foregroundColor: Colors.white,
          ),
          child: _isSubmitting
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                  ),
                )
              : const Text('Thêm'),
        ),
      ],
    );
  }
}
