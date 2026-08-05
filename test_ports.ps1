$ips = @("192.168.88.50", "192.168.88.63", "192.168.88.72", "192.168.88.74")
foreach ($ip in $ips) {
    Write-Host "Testing port 5555 on $ip..."
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $result = $tcp.BeginConnect($ip, 5555, $null, $null)
        $success = $result.AsyncWaitHandle.WaitOne(500, $false)
        if ($success) {
            Write-Host "[FOUND] ADB Port 5555 is open on $ip"
        }
        $tcp.Close()
    } catch { }
    
    Write-Host "Testing port 8080 on $ip..."
    try {
        $tcp2 = New-Object System.Net.Sockets.TcpClient
        $result2 = $tcp2.BeginConnect($ip, 8080, $null, $null)
        $success2 = $result2.AsyncWaitHandle.WaitOne(500, $false)
        if ($success2) {
            Write-Host "[FOUND] Web Server 8080 is open on $ip"
        }
        $tcp2.Close()
    } catch { }
}
