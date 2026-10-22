param(
    [string]$HostName = $env:REDIS_HOST,
    [int]$Port = $(if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } else { 6379 }),
    [string]$Password = $env:REDIS_PASSWORD,
    [long]$TrainingId = 14001
)

if ([string]::IsNullOrWhiteSpace($HostName)) { $HostName = '192.168.10.102' }
if ([string]::IsNullOrWhiteSpace($Password)) { $Password = '123456' }

function Send-RedisCommand([string[]]$Parts) {
    $client = [System.Net.Sockets.TcpClient]::new($HostName, $Port)
    try {
        $stream = $client.GetStream()
        $writer = [System.IO.StreamWriter]::new($stream, [System.Text.Encoding]::ASCII, 1024, $true)
        $writer.NewLine = "`r`n"
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
        $writer.WriteLine("*" + $Parts.Count)
        foreach ($part in $Parts) {
            $writer.WriteLine("$" + [System.Text.Encoding]::UTF8.GetByteCount($part))
            $writer.WriteLine($part)
        }
        $writer.Flush()
        return $reader.ReadLine()
    } finally {
        $client.Dispose()
    }
}

$keys = @(
    "campustrain:enrollment:stock:$TrainingId",
    "campustrain:enrollment:users:$TrainingId",
    "campustrain:enrollment:init:$TrainingId",
    "campustrain:enrollment:init-lock:$TrainingId"
)
if ((Send-RedisCommand @('AUTH', $Password)) -notmatch '^\+OK') { throw 'Redis authentication failed.' }
# AUTH uses a one-command connection, so use a direct reusable raw client for DELETE.
$client = [System.Net.Sockets.TcpClient]::new($HostName, $Port)
try {
    $stream = $client.GetStream(); $writer = [System.IO.StreamWriter]::new($stream, [Text.Encoding]::ASCII, 1024, $true); $reader = [System.IO.StreamReader]::new($stream, [Text.Encoding]::ASCII, $false, 1024, $true)
    $writer.NewLine = "`r`n"
    $commands = @()
    $commands += ,@('AUTH', $Password)
    $commands += ,(@('DEL') + $keys)
    foreach ($parts in $commands) {
        $writer.WriteLine("*" + $parts.Count); foreach ($part in $parts) { $writer.WriteLine("$" + [Text.Encoding]::UTF8.GetByteCount($part)); $writer.WriteLine($part) }; $writer.Flush(); $null = $reader.ReadLine()
    }
} finally { $client.Dispose() }
Write-Host "Cleared Stage14 Redis enrollment state for training $TrainingId."
