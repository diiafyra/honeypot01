import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:honeypot01/provider/ai_key_provider.dart';
import 'package:honeypot01/provider/label_provider.dart';
import 'package:honeypot01/screens/ai_config_screen.dart';
import 'package:provider/provider.dart';
import 'firebase_options.dart';
import 'screens/spam_list_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AIKeyProvider()),
        ChangeNotifierProvider(create: (_) => LabelProvider()),
      ],
      child: const MyApp(),
    ),
  );
}


class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Honeypot',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
      ),
      home: const MyHomePage(title: 'Honeypot'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});
  final String title;
  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  int _selectedIndex = 1; // default to Spam List
  final int _pageCount = 4;
  late final List<GlobalKey<NavigatorState>> _navKeys = List.generate(
    4,
    (_) => GlobalKey<NavigatorState>(),
  );

  Widget _buildNavigator(int index, Widget child) {
    return Navigator(
      key: _navKeys[index],
      onGenerateRoute: (settings) =>
          MaterialPageRoute(builder: (_) => child, settings: settings),
    );
  }

  void _onItemTapped(int index) => setState(() => _selectedIndex = index);

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () async {
        final currentNavigator = _navKeys[_selectedIndex].currentState;
        if (currentNavigator != null && currentNavigator.canPop()) {
          currentNavigator.pop();
          return false;
        }
        return true; // allow system to handle (exit app)
      },
      child: Scaffold(
        body: SafeArea(
          child: IndexedStack(
            index: _selectedIndex,
            children: [
              _buildNavigator(
                0,
                const Center(child: Text('Home (placeholder)')),
              ),
              _buildNavigator(1, SpamListScreen(navKey: _navKeys[1])),
              _buildNavigator(
                2,
                AIConfigScreen(navKey: _navKeys[2])
              ),
              _buildNavigator(
                3,
                const Center(child: Text('Export (placeholder)')),
              ),
            ],
          ),
        ),
        bottomNavigationBar: BottomNavigationBar(
          currentIndex: _selectedIndex,
          onTap: _onItemTapped,
          type: BottomNavigationBarType.fixed,
          backgroundColor: Colors.white,
          selectedItemColor: Colors.black87,
          unselectedItemColor: Colors.black54,
          items: const [
            BottomNavigationBarItem(
              icon: Icon(Icons.home_outlined),
              label: 'Home',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.list_alt),
              label: 'Spam List',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.smart_toy_outlined),
              label: 'AI Config',
            ),
            BottomNavigationBarItem(
              icon: Icon(Icons.upload_outlined),
              label: 'Export',
            ),
          ],
        ),
      ),
    );
  }
}
