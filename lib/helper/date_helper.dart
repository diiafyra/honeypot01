import 'package:intl/intl.dart';

String formatDate(int millis) {
  final date = DateTime.fromMillisecondsSinceEpoch(millis);
  return DateFormat('dd/MM/yyyy').format(date);
}
