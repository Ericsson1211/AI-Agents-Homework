// ADB Bridge API - Run on Mac to expose ADB commands via REST API
// This allows n8n running in Docker to call ADB commands on the host

const express = require('express');
const { exec } = require('child_process');
const app = express();

app.use(express.json());

// Endpoint: List devices
app.get('/adb/devices', (req, res) => {
  exec('adb devices -l', (error, stdout, stderr) => {
    if (error) {
      return res.status(500).json({ error: error.message });
    }
    res.json({ output: stdout, error: stderr });
  });
});

// Endpoint: Check installed packages
app.post('/adb/packages', (req, res) => {
  const { device, package_name } = req.body;
  const cmd = package_name 
    ? `adb -s ${device} shell pm list packages | grep ${package_name}`
    : `adb -s ${device} shell pm list packages`;
  
  exec(cmd, (error, stdout, stderr) => {
    if (error && error.code !== 1) { // grep returns 1 if not found
      return res.status(500).json({ error: error.message });
    }
    res.json({ output: stdout, error: stderr });
  });
});

// Endpoint: Install APK
app.post('/adb/install', (req, res) => {
  const { device, apk_path } = req.body;
  const cmd = `adb -s ${device} install -r "${apk_path}"`;
  
  exec(cmd, (error, stdout, stderr) => {
    if (error) {
      return res.status(500).json({ error: error.message, output: stderr });
    }
    res.json({ output: stdout, error: stderr, success: true });
  });
});

// Endpoint: List all configured AVDs (Android Virtual Devices)
app.get('/adb/avds', (req, res) => {
  exec('emulator -list-avds', (error, stdout, stderr) => {
    if (error) {
      return res.status(500).json({ error: error.message, stderr: stderr });
    }
    
    // Parse AVD list
    const avds = stdout.trim().split('\n').filter(name => name.length > 0);
    
    // Get running devices to mark which AVDs are already running
    exec('adb devices', (err, devicesOutput) => {
      const runningDevices = devicesOutput.split('\n')
        .filter(line => line.includes('emulator-'))
        .map(line => line.split('\t')[0]);
      
      const avdList = avds.map(avdName => ({
        name: avdName,
        running: false, // We'll check this below
        emulator_port: null
      }));
      
      res.json({ 
        avds: avdList,
        total_count: avdList.length,
        running_count: runningDevices.length,
        available_count: avdList.length - runningDevices.length
      });
    });
  });
});

// Endpoint: Start an emulator
app.post('/adb/start-emulator', (req, res) => {
  const { avd_name, options } = req.body;
  
  if (!avd_name) {
    return res.status(400).json({ error: 'avd_name is required' });
  }
  
  // Build emulator command with options
  let cmd = `emulator -avd ${avd_name}`;
  
  // Add common options if provided
  if (options) {
    if (options.no_window) cmd += ' -no-window';
    if (options.no_audio) cmd += ' -no-audio';
    if (options.no_boot_anim) cmd += ' -no-boot-anim';
    if (options.gpu) cmd += ` -gpu ${options.gpu}`;
  }
  
  // Start emulator in background (non-blocking)
  cmd += ' &';
  
  exec(cmd, (error, stdout, stderr) => {
    if (error) {
      return res.status(500).json({ 
        error: error.message, 
        stderr: stderr,
        success: false 
      });
    }
    
    res.json({ 
      message: `Emulator ${avd_name} starting...`,
      avd_name: avd_name,
      output: stdout,
      success: true,
      note: 'Emulator is starting in background. Use /adb/devices to check when ready.'
    });
  });
});

// Endpoint: Stop/kill an emulator
app.post('/adb/stop-emulator', (req, res) => {
  const { device } = req.body;
  
  if (!device) {
    return res.status(400).json({ error: 'device serial is required' });
  }
  
  const cmd = `adb -s ${device} emu kill`;
  
  exec(cmd, (error, stdout, stderr) => {
    if (error) {
      return res.status(500).json({ 
        error: error.message,
        stderr: stderr,
        success: false 
      });
    }
    
    res.json({ 
      message: `Emulator ${device} stopped`,
      device: device,
      output: stdout,
      success: true
    });
  });
});

// Endpoint: Execute custom ADB command
app.post('/adb/command', (req, res) => {
  const { command } = req.body;
  
  // Security: Only allow adb commands
  if (!command.startsWith('adb')) {
    return res.status(400).json({ error: 'Only ADB commands allowed' });
  }
  
  exec(command, (error, stdout, stderr) => {
    if (error) {
      return res.status(500).json({ error: error.message });
    }
    res.json({ output: stdout, error: stderr });
  });
});

// In-memory storage for agent memory (in production, use database)
const agentMemory = new Map();

// Endpoint: Save memory
app.post('/adb/save-memory', (req, res) => {
  const { memory_key, memory_value } = req.body;
  
  if (!memory_key || !memory_value) {
    return res.status(400).json({ error: 'memory_key and memory_value are required' });
  }
  
  agentMemory.set(memory_key, {
    value: memory_value,
    updated_at: new Date().toISOString()
  });
  
  res.json({
    success: true,
    memory_key: memory_key,
    memory_value: memory_value,
    message: 'Memory saved successfully'
  });
});

// Endpoint: Get memory
app.post('/adb/get-memory', (req, res) => {
  const { memory_key } = req.body;
  
  if (!memory_key) {
    return res.status(400).json({ error: 'memory_key is required' });
  }
  
  const memory = agentMemory.get(memory_key);
  
  if (!memory) {
    return res.status(404).json({ 
      error: 'Memory not found',
      memory_key: memory_key
    });
  }
  
  res.json({
    success: true,
    memory_key: memory_key,
    memory_value: memory.value,
    updated_at: memory.updated_at
  });
});

// Endpoint: List all saved memories
app.get('/adb/list-memories', (req, res) => {
  const memories = [];
  
  for (const [key, data] of agentMemory.entries()) {
    memories.push({
      key: key,
      value: data.value,
      updated_at: data.updated_at
    });
  }
  
  res.json({
    memories: memories,
    count: memories.length
  });
});

const PORT = 3000;
app.listen(PORT, () => {
  console.log(`\n🚀 ADB Bridge API running on http://localhost:${PORT}`);
  console.log('\nAvailable endpoints:');
  console.log('  GET  /adb/devices        - List running devices');
  console.log('  GET  /adb/avds           - List all configured AVDs');
  console.log('  POST /adb/packages       - body: {device, package_name?}');
  console.log('  POST /adb/install        - body: {device, apk_path}');
  console.log('  POST /adb/start-emulator - body: {avd_name, options?}');
  console.log('  POST /adb/stop-emulator  - body: {device}');
  console.log('  POST /adb/command        - body: {command}');
  console.log('  POST /adb/save-memory    - body: {memory_key, memory_value}');
  console.log('  POST /adb/get-memory     - body: {memory_key}');
  console.log('  GET  /adb/list-memories  - List all saved memories');
  console.log('\nPress Ctrl+C to stop\n');
});
