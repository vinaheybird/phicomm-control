$subnet = "192.168.88"
$tasks = @()

Write-Host "Scanning $subnet.x on port 8080..."

1..254 | ForEach-Object {
    $ip = "$subnet.$_"
    
    $runspace = [powershell]::Create().AddScript({
        param($targetIp)
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $result = $tcp.BeginConnect($targetIp, 8080, $null, $null)
            $success = $result.AsyncWaitHandle.WaitOne(300, $false)
            if ($success) {
                if ($tcp.Connected) {
                    $tcp.Close()
                    return $targetIp
                }
            }
            $tcp.Close()
        } catch { }
        return $null
    }).AddArgument($ip)
    
    $tasks += [PSCustomObject]@{
        Runspace = $runspace
        Result = $runspace.BeginInvoke()
    }
}

foreach ($task in $tasks) {
    $found = $task.Runspace.EndInvoke($task.Result)
    $task.Runspace.Dispose()
    if ($found) {
        $ipStr = $found -join ""
        if (![string]::IsNullOrWhiteSpace($ipStr)) {
            Write-Host "[FOUND] Web Config Server is running at http://${ipStr}:8080"
        }
    }
}
Write-Host "Scan complete."
