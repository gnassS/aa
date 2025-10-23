import 'package:flutter/material.dart';
import 'package:wake_on_lan/wake_on_lan.dart';
import 'package:http/http.dart' as http; // Cho HTTP request
import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart' as p;

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'WOL & Shutdown',
      theme: ThemeData(primarySwatch: Colors.blue),
      home: const DeviceListScreen(),
    );
  }
}

class DeviceListScreen extends StatefulWidget {
  const DeviceListScreen({super.key});

  @override
  DeviceListScreenState createState() => DeviceListScreenState();
}

class DeviceListScreenState extends State<DeviceListScreen> {
  List<Map<String, dynamic>> devices = [];
  Database? database;

  @override
  void initState() {
    super.initState();
    initDatabase().then((_) {
      loadDevices();
    });
  }

  // Khởi tạo database
  Future<void> initDatabase() async {
    database = await openDatabase(
      p.join(await getDatabasesPath(), 'devices.db'),
      onCreate: (db, version) {
        return db.execute(
          'CREATE TABLE devices(id INTEGER PRIMARY KEY, name TEXT, ip TEXT, mac TEXT, key TEXT, port_wol INTEGER DEFAULT 9)',
        );
      },
      version: 1,
    );
    debugPrint('Database initialized');
  }

  // Load danh sách thiết bị
  Future<void> loadDevices() async {
    if (database == null) return;
    final List<Map<String, dynamic>> maps = await database!.query('devices');
    if (mounted) {
      setState(() {
        devices = maps;
      });
    }
    debugPrint('Loaded ${maps.length} devices');
  }

  // Dialog form thêm hoặc edit thiết bị
  Future<void> deviceFormDialog({Map<String, dynamic>? device}) async {
    final isEdit = device != null;
    final nameController = TextEditingController(
      text: isEdit ? device['name'] : '',
    );
    final ipController = TextEditingController(
      text: isEdit ? device['ip'] : '',
    );
    final macController = TextEditingController(
      text: isEdit ? device['mac'] : '',
    );
    final keyController = TextEditingController(
      text: isEdit ? device['key'] : '',
    ); // Key cho HTTP
    final portController = TextEditingController(
      text: isEdit ? device['port_wol'].toString() : '9',
    );

    showDialog(
      context: context,
      builder: (BuildContext dialogCtx) => AlertDialog(
        title: Text(isEdit ? 'Sửa Thiết Bị' : 'Thêm Thiết Bị'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'Tên thiết bị (ví dụ: PC Văn Phòng)',
                ),
              ),
              TextField(
                controller: ipController,
                decoration: const InputDecoration(
                  labelText: 'IP PC (ví dụ: 192.168.1.100)',
                ),
              ),
              TextField(
                controller: macController,
                decoration: const InputDecoration(
                  labelText: 'MAC (ví dụ: AA:BB:CC:DD:EE:FF)',
                ),
              ),
              TextField(
                controller: portController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(
                  labelText: 'Port WOL (mặc định 9)',
                ),
              ),
              TextField(
                controller: keyController,
                decoration: const InputDecoration(
                  labelText:
                      'Key HTTP (tùy chọn cho shutdown, ví dụ: myapp123)',
                ),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () {
              final scaffoldMessenger = ScaffoldMessenger.of(context);
              final navigator = Navigator.of(dialogCtx);

              () async {
                if (nameController.text.isEmpty ||
                    ipController.text.isEmpty ||
                    macController.text.isEmpty) {
                  if (mounted) {
                    scaffoldMessenger.showSnackBar(
                      const SnackBar(
                        content: Text('Vui lòng nhập tên, IP, MAC!'),
                      ),
                    );
                  }
                  return;
                }
                final port = int.tryParse(portController.text) ?? 9;

                final data = {
                  'name': nameController.text,
                  'ip': ipController.text,
                  'mac': macController.text,
                  'key': keyController.text, // Key cho HTTP
                  'port_wol': port,
                };

                try {
                  if (isEdit) {
                    await database!.update(
                      'devices',
                      data,
                      where: 'id = ?',
                      whereArgs: [device['id']],
                    );
                    debugPrint('Updated device: ${nameController.text}');
                  } else {
                    await database!.insert('devices', data);
                    debugPrint('Added new device: ${nameController.text}');
                  }
                } catch (e) {
                  debugPrint('DB error: $e');
                  if (mounted) {
                    scaffoldMessenger.showSnackBar(
                      SnackBar(content: Text('Lỗi lưu dữ liệu: $e')),
                    );
                  }
                  return;
                }

                await loadDevices();
                if (navigator.canPop()) {
                  navigator.pop();
                }
                if (mounted) {
                  scaffoldMessenger.showSnackBar(
                    SnackBar(content: Text(isEdit ? 'Đã sửa!' : 'Đã thêm!')),
                  );
                }
              }();
            },
            child: const Text('Lưu'),
          ),
        ],
      ),
    );
  }

  // Xóa thiết bị (giữ nguyên)
  Future<void> deleteDevice(int id, String name) async {
    showDialog(
      context: context,
      builder: (BuildContext dialogCtx) => AlertDialog(
        title: const Text('Xác Nhận Xóa'),
        content: Text('Bạn chắc chắn muốn xóa "$name"?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () {
              final scaffoldMessenger = ScaffoldMessenger.of(context);
              final navigator = Navigator.of(dialogCtx);

              () async {
                try {
                  await database!.delete(
                    'devices',
                    where: 'id = ?',
                    whereArgs: [id],
                  );
                  await loadDevices();
                  debugPrint('Deleted device ID: $id');
                } catch (e) {
                  debugPrint('DB delete error: $e');
                  if (mounted) {
                    scaffoldMessenger.showSnackBar(
                      SnackBar(content: Text('Lỗi xóa: $e')),
                    );
                  }
                  return;
                }

                if (navigator.canPop()) {
                  navigator.pop();
                }
                if (mounted) {
                  scaffoldMessenger.showSnackBar(
                    const SnackBar(content: Text('Đã xóa!')),
                  );
                }
              }();
            },
            child: const Text('Xóa'),
          ),
        ],
      ),
    );
  }

  // Wake-on-LAN (giữ nguyên)
  Future<void> wakeDevice(String mac, String ip, int port) async {
    try {
      final ipValidation = IPAddress.validate(ip);
      if (!ipValidation.state) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Lỗi IP: ${ipValidation.error}')),
          );
        }
        return;
      }
      final macValidation = MACAddress.validate(mac);
      if (!macValidation.state) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Lỗi MAC: ${macValidation.error}')),
          );
        }
        return;
      }
      final ipAddress = IPAddress(ip);
      final macAddress = MACAddress(mac);
      final wakeOnLan = WakeOnLAN(ipAddress, macAddress, port: port);
      await wakeOnLan.wake();
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Đã wake $ip!')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Lỗi WOL: $e')));
      }
    }
  }

  // Shutdown qua HTTP (cho Windows)
  Future<void> shutdownDevice(String ip, String key) async {
    if (key.isEmpty) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Cần key HTTP để shutdown!')),
        );
      }
      return;
    }
    try {
      final response = await http.post(
        Uri.parse('http://$ip:8080/'),
        headers: {'Content-Type': 'application/json'},
        body: '{"key": "$key"}',
      );
      if (response.statusCode == 200) {
        final json = response.body;
        debugPrint('Shutdown response: $json');
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Đã gửi lệnh shutdown $ip!')));
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Lỗi HTTP: ${response.statusCode}')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Lỗi shutdown: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('WOL & Shutdown')),
      body: devices.isEmpty
          ? const Center(child: Text('Nhấn nút + để thêm thiết bị!'))
          : ListView.builder(
              itemCount: devices.length,
              itemBuilder: (BuildContext listCtx, index) {
                final device = devices[index];
                return Card(
                  child: ListTile(
                    title: Text(device['name']),
                    subtitle: Text(
                      'IP: ${device['ip']} - Port WOL: ${device['port_wol']}',
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.power, color: Colors.green),
                          onPressed: () => wakeDevice(
                            device['mac'],
                            device['ip'],
                            device['port_wol'],
                          ),
                        ),
                        IconButton(
                          icon: const Icon(
                            Icons.power_settings_new,
                            color: Colors.red,
                          ),
                          onPressed: () =>
                              shutdownDevice(device['ip'], device['key']),
                        ),
                        IconButton(
                          icon: const Icon(Icons.edit, color: Colors.blue),
                          onPressed: () => deviceFormDialog(device: device),
                        ),
                        IconButton(
                          icon: const Icon(
                            Icons.delete,
                            color: Colors.redAccent,
                          ),
                          onPressed: () =>
                              deleteDevice(device['id'], device['name']),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => deviceFormDialog(),
        child: const Icon(Icons.add),
      ),
    );
  }
}
