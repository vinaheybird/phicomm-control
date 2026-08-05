$subnet = "192.168.88"
$tasks = @()

Write-Host "Pinging $subnet.x (ICMP)..."

1..254 | ForEach-Object {
    $ip = "$subnet.$_"
    
    $runspace = [powershell]::Create().AddScript({
        param($targetIp)
        try {
            if (Test-Connection -ComputerName $targetIp -Count 1 -Quiet -TimeoutSeconds 1) {
                return $targetIp
            }
        } catch { }
        return $null
    }).AddArgument($ip)
    
    $tasks += [PSCustomObject]@{
        Runspace = $runspace
        Result = $runspace.BeginInvoke()
    }
}

$foundIps = @()
foreach ($task in $tasks) {
    $found = $task.Runspace.EndInvoke($task.Result)
    $task.Runspace.Dispose()
    if ($found) {
        $ipStr = $found -join ""
        if (![string]::IsNullOrWhiteSpace($ipStr)) {
            $foundIps += $ipStr
            Write-Host "[ALIVE] $ipStr"
        }
    }
}
Write-Host "Ping Sweep complete."
