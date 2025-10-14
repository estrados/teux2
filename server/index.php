<?php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: POST, GET, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');
header('Content-Type: application/json');

// Handle preflight requests
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$logFile = __DIR__ . '/logs.txt';

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Receive log data from Android app
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);

    if ($data) {
        $timestamp = date('Y-m-d H:i:s');
        $method = $data['method'] ?? 'UNKNOWN';
        $status = $data['status'] ?? 'UNKNOWN';
        $message = $data['message'] ?? '';
        $responseTime = $data['response_time'] ?? 'N/A';

        $logEntry = sprintf(
            "[%s] Method: %s | Status: %s | Time: %sms | Message: %s\n",
            $timestamp,
            $method,
            $status,
            $responseTime,
            $message
        );

        file_put_contents($logFile, $logEntry, FILE_APPEND);

        echo json_encode(['success' => true, 'message' => 'Log received']);
    } else {
        echo json_encode(['success' => false, 'message' => 'Invalid data']);
    }
} elseif ($_SERVER['REQUEST_METHOD'] === 'GET') {
    // Display logs
    if (isset($_GET['view'])) {
        header('Content-Type: text/plain');
        if (file_exists($logFile)) {
            echo file_get_contents($logFile);
        } else {
            echo "No logs yet.\n";
        }
    } else {
        // Simple HTML interface
        ?>
        <!DOCTYPE html>
        <html>
        <head>
            <title>KitKat SSL Test Logs</title>
            <style>
                body { font-family: monospace; padding: 20px; }
                pre { background: #f4f4f4; padding: 10px; border-radius: 5px; }
                h1 { color: #333; }
            </style>
        </head>
        <body>
            <h1>KitKat SSL Test Logs</h1>
            <p><a href="?view=logs">View Raw Logs</a> | <a href="?clear=1">Clear Logs</a></p>
            <pre><?php
                if (file_exists($logFile)) {
                    echo htmlspecialchars(file_get_contents($logFile));
                } else {
                    echo "No logs yet.";
                }
            ?></pre>
            <script>
                // Auto-refresh every 3 seconds
                setTimeout(function(){ location.reload(); }, 3000);
            </script>
        </body>
        </html>
        <?php
    }
} elseif (isset($_GET['clear'])) {
    file_put_contents($logFile, '');
    header('Location: /');
    exit;
}
?>
