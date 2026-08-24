<?php
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    exit(0);
}

set_time_limit(600);
ini_set('memory_limit', '1024M');

putenv('HTTP_USER_AGENT=');
putenv('HTTP_SEC_CH_UA=');
putenv('HTTP_SEC_FETCH_DEST=');
putenv('HTTP_SEC_FETCH_MODE=');
putenv('HTTP_SEC_FETCH_SITE=');
putenv('HTTP_ORIGIN=');
putenv('HTTP_REFERER=');

// ==========================================
// التعديل الأساسي: مسارات الأندرويد المحلية
// ==========================================
$wwwDir = __DIR__;                     // مجلد www الحالي
$appRootDir = dirname($wwwDir);        // المجلد الرئيسي للتطبيق
$binDir = $appRootDir . '/bin';        // مجلد الأدوات الثنائية
$tmpDirApp = $appRootDir . '/tmp';     // مجلد مؤقت خاص بالتطبيق

// إنشاء المجلد المؤقت إذا لم يكن موجوداً
if (!file_exists($tmpDirApp)) {
    @mkdir($tmpDirApp, 0777, true);
}

$ytdlpBin = $binDir . '/yt-dlp';
$ffmpegBin = $binDir . '/ffmpeg';      // تحديد مسار FFmpeg بدقة

// دالة التحديث ستعمل بنجاح لأن مجلد التطبيق قابل للكتابة
function updateYtdlp($bin) {
    static $updated = false;
    if (!$updated && file_exists($bin)) {
        @exec($bin . ' -U > /dev/null 2>&1');
        $updated = true;
    }
}

function getBaseArgs($url = '') {
    global $ffmpegBin;
    
    // إضافة مسار ffmpeg المحلي حتى يتعرف عليه yt-dlp في الأندرويد
    $args = '--quiet --no-warnings --ffmpeg-location ' . escapeshellarg($ffmpegBin) . ' --extractor-args "youtube:player_client=android,web_embedded;skip=dash,hls"';
    
    if (stripos($url, 'pin.it') !== false || stripos($url, 'pinterest.com') !== false) {
        $args .= ' --extractor-args "pinterest:download_video=True" --no-playlist --ignore-no-formats-error';
    }
    if (stripos($url, 'instagram.com') !== false || stripos($url, 'instagr.am') !== false) {
        $args .= ' --extractor-args "instagram:download_video=True" --no-playlist';
    }
    $args .= ' --add-header "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"';
    $args .= ' --geo-bypass';
    if (file_exists(__DIR__ . '/cookies.txt')) {
        $args .= ' --cookies ' . escapeshellarg(__DIR__ . '/cookies.txt');
    }
    return $args;
}

function cleanYouTubeUrl($url) {
    if (preg_match('/(?:youtu\.be\/|youtube\.com\/(?:embed\/|v\/|watch\?v=|watch\?.+&v=))([\w-]{11})/', $url, $matches)) {
        return 'https://www.youtube.com/watch?v=' . $matches[1];
    }
    return $url;
}

$action = $_GET['action'] ?? '';

// ... [كود جلب المعلومات info يبقى كما هو بالضبط] ...
if ($_SERVER['REQUEST_METHOD'] === 'POST' && $action === 'info') {
    updateYtdlp($ytdlpBin);
    $input = json_decode(file_get_contents('php://input'), true);
    $url = $input['url'] ?? '';

    if (empty($url)) {
        http_response_code(400);
        echo json_encode(['error' => 'URL is required']) . "\n";
        exit;
    }

    $url = cleanYouTubeUrl($url);
    $baseArgs = getBaseArgs($url);
    $cmd = escapeshellarg($ytdlpBin) . " {$baseArgs} -J " . escapeshellarg($url) . " 2>&1";
    $output = shell_exec($cmd);
    $info = json_decode($output, true);

    // ... (بقية كود معالجة البيانات وبناء الجودة بدون تغيير) ...
    // لقد قمت باختصار هذا الجزء لعدم الإطالة، يمكنك وضع نفس الكود الخاص بك هنا
}

if ($_SERVER['REQUEST_METHOD'] === 'GET' && $action === 'download') {
    updateYtdlp($ytdlpBin);
    $url = $_GET['url'] ?? '';
    $formatId = $_GET['format_id'] ?? 'best';

    if (empty($url)) {
        http_response_code(400);
        echo json_encode(['error' => 'URL is required']) . "\n";
        exit;
    }

    $url = cleanYouTubeUrl($url);
    $baseArgs = getBaseArgs($url);

    // ... [كود تحميل الصور يبقى كما هو] ...

    $titleCmd = escapeshellarg($ytdlpBin) . " {$baseArgs} --get-title " . escapeshellarg($url);
    $rawTitle = trim(shell_exec($titleCmd));
    $cleanTitle = !empty($rawTitle) ? preg_replace('/[^\w\s\d\-_~.]/u', '_', $rawTitle) : 'video_' . uniqid();

    // التعديل هنا: استخدام المجلد المؤقت الخاص بالتطبيق بدلاً من مجلد النظام
    global $tmpDirApp;
    $tmpDir = $tmpDirApp . '/grab_' . uniqid();
    mkdir($tmpDir, 0777, true);
    $outputTemplate = $tmpDir . '/out.%(ext)s';

    // التعديل هنا: دمج الفيديو والصوت باستخدام ffmpeg المحلي
    $cmd = escapeshellarg($ytdlpBin) . " {$baseArgs} -f " . escapeshellarg($formatId) . " --merge-output-format mp4 -o " . escapeshellarg($outputTemplate) . " " . escapeshellarg($url);
    exec($cmd);

    $files = glob($tmpDir . '/out.*');
    if (empty($files) || !file_exists($files[0])) {
        $cmd = escapeshellarg($ytdlpBin) . " {$baseArgs} -f best -o " . escapeshellarg($outputTemplate) . " " . escapeshellarg($url);
        exec($cmd);
        $files = glob($tmpDir . '/out.*');
    }

    if (empty($files) || !file_exists($files[0])) {
        http_response_code(500);
        echo json_encode(['error' => 'Download or merging failed']) . "\n";
        @rmdir($tmpDir);
        exit;
    }

    $downloadFile = $files[0];
    $ext = pathinfo($downloadFile, PATHINFO_EXTENSION);
    $filename = $cleanTitle . '.' . $ext;

    header('Content-Description: File Transfer');
    header('Content-Type: application/octet-stream');
    header('Content-Disposition: attachment; filename="' . rawurlencode($filename) . '"; filename*=UTF-8\'\' ' . rawurlencode($filename));
    header('Expires: 0');
    header('Cache-Control: must-revalidate');
    header('Pragma: public');
    header('Content-Length: ' . filesize($downloadFile));

    readfile($downloadFile);

    @unlink($downloadFile);
    @rmdir($tmpDir);
    exit;
}

http_response_code(404);
echo json_encode(['error' => 'Invalid Endpoint']) . "\n";
