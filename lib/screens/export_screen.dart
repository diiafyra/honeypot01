import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';
import 'dart:io';
import 'dart:convert';
import '../models/spam_item.dart';

class ExportScreen extends StatefulWidget {
  const ExportScreen({super.key, required this.navKey});
  final GlobalKey<NavigatorState> navKey;

  @override
  State<ExportScreen> createState() => _ExportScreenState();
}

class _ExportScreenState extends State<ExportScreen> {
  int _selectedOption = 0;
  bool _isLoading = false;

  static const String _documentsPath =
      '/storage/emulated/0/Documents/honeypot1';

  Future<List<SpamItem>> _fetchSpamItems() async {
    final snapshot = await FirebaseFirestore.instance
        .collection('spam_numbers')
        .orderBy('last_seen', descending: true)
        .get();

    return snapshot.docs.map((doc) => SpamItem.fromDoc(doc)).toList();
  }

  Future<void> _exportCsv() async {
    setState(() => _isLoading = true);

    try {
      if (_selectedOption == 2) {
        // Export latest month/year only
        final items = await _fetchSpamItems();

        if (!mounted) return;

        if (items.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Không có mục nào để xuất')),
          );
          setState(() => _isLoading = false);
          return;
        }

        // Find the latest month from all items
        String latestMonthKey = '';
        for (final item in items) {
          if (item.lastSeen.millisecondsSinceEpoch > 0) {
            final monthKey = DateFormat('yyyyMM').format(item.lastSeen);
            if (latestMonthKey.isEmpty ||
                monthKey.compareTo(latestMonthKey) > 0) {
              latestMonthKey = monthKey;
            }
          }
        }

        if (latestMonthKey.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Không có mục nào có ngày hợp lệ để xuất'),
            ),
          );
          setState(() => _isLoading = false);
          return;
        }

        // Filter items for the latest month
        final latestMonthItems = items
            .where(
              (item) =>
                  item.lastSeen.millisecondsSinceEpoch > 0 &&
                  DateFormat('yyyyMM').format(item.lastSeen) == latestMonthKey,
            )
            .toList();

        if (latestMonthItems.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Không có mục nào để xuất')),
          );
          setState(() => _isLoading = false);
          return;
        }

        // Create file for latest month
        final header = 'phone,label';
        final rows = latestMonthItems
            .map((it) => '"${it.id}","${it.label}"')
            .join('\n');
        final filename = 'spamNumbers_$latestMonthKey.csv';
        final filePath = await _saveFile('$header\n$rows', filename);

        if (!mounted) return;

        _showExportDialog(filePath);
      } else {
        // Original logic for options 0 and 1
        List<SpamItem> items;

        if (_selectedOption == 0) {
          items = await _fetchSpamItems();
        } else {
          items = await _fetchSpamItems();
        }

        if (!mounted) return;

        if (items.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Không có mục nào để xuất')),
          );
          setState(() => _isLoading = false);
          return;
        }

        String header;
        String rows;

        if (_selectedOption == 1) {
          header = 'phone';
          rows = items.map((it) => '"${it.id}"').join('\n');
        } else {
          header = 'phone,label';
          rows = items.map((it) => '"${it.id}","${it.label}"').join('\n');
        }

        final now = DateTime.now();
        final timestamp = DateFormat('yyyyMMdd_HHmmss').format(now);
        final filename = 'spamNumbers_$timestamp.csv';

        final filePath = await _saveFile('$header\n$rows', filename);

        if (!mounted) return;

        _showExportDialog(filePath);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Lỗi xuất dữ liệu: $e')));
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  Future<String> _saveFile(String content, String filename) async {
    try {
      final exportDir = Directory(_documentsPath);

      if (!await exportDir.exists()) {
        await exportDir.create(recursive: true);
      }

      // Add UTF-8 BOM for proper encoding in Excel
      final contentWithBOM = '\uFEFF$content';

      final file = File('${exportDir.path}/$filename');
      await file.writeAsString(contentWithBOM, encoding: utf8);

      return file.path;
    } catch (e) {
      throw 'Không thể lưu tệp: $e';
    }
  }

  void _showExportDialog(String filePath) {
    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Xuất dữ liệu'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Tệp đã được lưu tại đây',
                style: TextStyle(fontSize: 14, color: Colors.black87),
              ),
              const SizedBox(height: 12),
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.grey.shade100,
                  borderRadius: BorderRadius.circular(4),
                  border: Border.all(color: Colors.blue.shade200),
                ),
                child: SelectableText(
                  filePath,
                  style: const TextStyle(
                    fontSize: 12,
                    color: Colors.blue,
                    fontFamily: 'Courier',
                  ),
                  maxLines: 4,
                ),
              ),
            ],
          ),
          actions: [
            TextButton.icon(
              onPressed: () async {
                try {
                  await Share.shareXFiles([
                    XFile(filePath),
                  ], text: 'Xuất dữ liệu số spam');
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Không thể chia sẻ tệp: $e')),
                    );
                  }
                }
              },
              icon: const Icon(Icons.share_outlined),
              label: const Text('Chia sẻ'),
              style: TextButton.styleFrom(foregroundColor: Colors.blue),
            ),
            const SizedBox(width: 8),
            ElevatedButton.icon(
              onPressed: () async {
                try {
                  await Share.shareXFiles([
                    XFile(filePath),
                  ], text: 'Xuất dữ liệu số spam');
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Không thể mở tệp: $e')),
                    );
                  }
                }
                if (dialogContext.mounted) Navigator.pop(dialogContext);
              },
              icon: const Icon(Icons.folder_open),
              label: const Text('Mở'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
              ),
            ),
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF8F8FB),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16.0),
                child: Text(
                  'EXPORT',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                    color: Colors.black87,
                  ),
                ),
              ),
              const Padding(
                padding: EdgeInsets.only(bottom: 32.0),
                child: Text(
                  'Danh sách để dễ export dữ liệu csv.',
                  style: TextStyle(fontSize: 14, color: Colors.black54),
                ),
              ),
              _buildRadioOption(
                value: 0,
                title: 'Xuất toàn bộ số và phân loại',
                description: 'Xuất tất cả số điện thoại kèm nhãn phân loại',
              ),
              const SizedBox(height: 16),
              _buildRadioOption(
                value: 1,
                title: 'Xuất toàn bộ số',
                description: 'Chỉ xuất số điện thoại',
              ),
              const SizedBox(height: 16),
              _buildRadioOption(
                value: 2,
                title: 'Xuất toàn bộ số và phân loại theo tháng',
                description: 'Xuất số điện thoại kèm nhãn từ tháng gần nhất',
              ),
              const SizedBox(height: 48),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _exportCsv,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                  child: _isLoading
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor: AlwaysStoppedAnimation<Color>(
                              Colors.white,
                            ),
                          ),
                        )
                      : const Text(
                          'Xuất dữ liệu',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildRadioOption({
    required int value,
    required String title,
    required String description,
  }) {
    return GestureDetector(
      onTap: () => setState(() => _selectedOption = value),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: _selectedOption == value
                ? Colors.blue
                : Colors.grey.shade300,
            width: _selectedOption == value ? 2 : 1,
          ),
        ),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
          child: Row(
            children: [
              Radio<int>(
                value: value,
                groupValue: _selectedOption,
                onChanged: (v) => setState(() => _selectedOption = v ?? 0),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                        color: Colors.black87,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      description,
                      style: const TextStyle(
                        fontSize: 12,
                        color: Colors.black54,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
