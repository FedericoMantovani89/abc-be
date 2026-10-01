<#
.SYNOPSIS
  Prepara il pacchetto di installazione del sito ABC (un solo file .zip) sul PC di Federico.

.DESCRIPTION
  Da due repository puliti e sul ramo main (abc-be e abc-fe), con il tag della versione gia' creato:
    1. controlla che tutto sia in ordine (repo puliti, ramo main, tag esistente e uguale a HEAD,
       nessun segreto o dato reale nei file da consegnare);
    2. esporta il codice DAL TAG (non dalla cartella di lavoro: niente file dimenticati);
    3. costruisce le immagini docker di frontend, backend e backup con la versione nel nome;
    4. le salva in file compressi (docker save + gzip) insieme a caddy e postgres;
    5. copia compose, Caddyfile, .env.esempio, script e documenti, scrive MANIFEST.txt e SHA256SUMS.txt;
    6. produce UN file abc-sito-<versione>-<AAAAMMGG>.zip fuori dai repository, con la sua impronta SHA-256.
  NON scrive MAI segreti: se trova un .env, una chiave o un file di dati reali, si ferma con errore.

  Con -ProvaASecco stampa tutto cio' che farebbe, esegue solo i controlli (in sola lettura) e NON
  costruisce, non salva e non scrive niente.

.PARAMETER Versione
  Il tag da pubblicare, per esempio v1.1.0. Deve esistere in tutti e due i repository ed essere
  il commit su cui sono entrambi (HEAD di main).

.PARAMETER RepoBackend
  Cartella di abc-be. Predefinito: il repository in cui si trova questo script.

.PARAMETER RepoFrontend
  Cartella di abc-fe. Predefinito: la cartella "abc-fe" accanto ad abc-be.

.PARAMETER CartellaUscita
  Dove finisce lo zip. Predefinito: <cartella sopra abc-be>\appoggio\pacchetto-<versione>
  (cioe' D:\workspaces\abc-website\appoggio\pacchetto-<versione>). Deve stare FUORI dai repository.

.PARAMETER NoteDiRilascio
  File di testo (Markdown) scritto da Federico con le note di questa versione. Se manca, le note
  vengono generate dai messaggi dei commit dall'ultima versione.

.PARAMETER ProvaASecco
  Stampa cosa farebbe e fa solo i controlli. Non costruisce niente.

.PARAMETER Sovrascrivi
  Permette di rifare uno zip che esiste gia' con lo stesso nome.

.EXAMPLE
  .\crea-pacchetto.ps1 -Versione v1.1.0 -ProvaASecco
  .\crea-pacchetto.ps1 -Versione v1.1.0
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string]$Versione,
  [string]$RepoBackend,
  [string]$RepoFrontend,
  [string]$CartellaUscita,
  [string]$NoteDiRilascio,
  [switch]$ProvaASecco,
  [switch]$Sovrascrivi
)

Set-StrictMode -Version 2
$ErrorActionPreference = 'Stop'
# Qualunque errore fuori dalla parte di costruzione: messaggio chiaro e uscita con errore.
trap {
  Write-Host ''
  Write-Host "ERRORE: $($_.Exception.Message)" -ForegroundColor Red
  exit 1
}

# ---------------------------------------------------------------- impostazioni
if (-not $RepoBackend)  { $RepoBackend  = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path }
$RepoBackend = (Resolve-Path $RepoBackend).Path
$cartellaSopra = Split-Path $RepoBackend -Parent
if (-not $RepoFrontend) { $RepoFrontend = Join-Path $cartellaSopra 'abc-fe' }
if (Test-Path $RepoFrontend) { $RepoFrontend = (Resolve-Path $RepoFrontend).Path }
if (-not $CartellaUscita) { $CartellaUscita = Join-Path (Join-Path $cartellaSopra 'appoggio') ("pacchetto-" + $Versione) }
$CartellaUscita = [System.IO.Path]::GetFullPath($CartellaUscita)

$Ramo = 'main'
$ImmagineCaddy = 'caddy:2-alpine'
$ImmaginePostgres = 'postgres:16-alpine'
$Data = Get-Date
$DataBreve = $Data.ToString('yyyyMMdd')
$NomePacchetto = "abc-sito-$Versione"
$NomeZip = "$NomePacchetto-$DataBreve.zip"
$Staging = Join-Path $CartellaUscita ("lavoro-" + $NomePacchetto)
$Esportazioni = Join-Path $CartellaUscita 'sorgenti-da-tag'
$script:Problemi = New-Object System.Collections.Generic.List[string]
$utf8SenzaBom = New-Object System.Text.UTF8Encoding($false)

# ---------------------------------------------------------------- messaggi
function Titolo($testo) { Write-Host ''; Write-Host "== $testo" -ForegroundColor Cyan }
function Ok($testo)     { Write-Host "   ok   $testo" -ForegroundColor Green }
function Info($testo)   { Write-Host "        $testo" }
function Simulo($testo) { Write-Host "   [PROVA A SECCO] $testo" -ForegroundColor Yellow }
# Con -ProvaASecco i problemi si raccolgono e si mostrano; senza, il primo problema ferma tutto.
function Problema($testo) {
  if ($ProvaASecco) {
    $script:Problemi.Add($testo)
    Write-Host "   [NON OK] $testo" -ForegroundColor Red
  } else {
    throw $testo
  }
}

# I programmi nativi (git, docker, tar) scrivono messaggi di avanzamento sul canale degli errori. Con
# $ErrorActionPreference = 'Stop' Windows PowerShell 5.1 trasforma la prima di quelle righe in un errore
# che ferma lo script, ma SOLO quando l'uscita viene registrata (pipe o redirezione su file). Per questo
# ogni chiamata nativa gira con 'Continue' e il risultato si controlla con $LASTEXITCODE (come up.ps1).
function InvocaGit([string]$repo, [string[]]$argomenti) {
  $ErrorActionPreference = 'Continue'
  $out = & git -C $repo @argomenti
  if ($LASTEXITCODE -ne 0) { throw "git $($argomenti -join ' ') ha fallito in $repo" }
  return $out
}
function InvocaGitSilenzioso([string]$repo, [string[]]$argomenti) {
  # per i comandi che possono "fallire" senza essere un errore (tag mancante): restituisce $null
  $ErrorActionPreference = 'Continue'
  $out = & git -C $repo @argomenti 2>$null
  if ($LASTEXITCODE -ne 0) { return $null }
  return $out
}
function Esegui([string]$descrizione, [scriptblock]$comando) {
  Info $descrizione
  # 'Continue' solo attorno alla chiamata nativa (vedi sopra); poi si controlla il codice di uscita.
  $ErrorActionPreference = 'Continue'
  & $comando
  $codice = $LASTEXITCODE
  $ErrorActionPreference = 'Stop'
  if ($codice -ne 0) { throw "Comando fallito ($descrizione), codice $codice" }
}

# ---------------------------------------------------------------- controlli sui repository
$Commit = @{}
function ControllaRepo([string]$nome, [string]$percorso) {
  Titolo "Controllo repository $nome ($percorso)"
  if (-not (Test-Path (Join-Path $percorso '.git'))) { Problema "${nome}: la cartella non e' un repository git: $percorso"; return }
  $sporco = @(InvocaGit $percorso @('status', '--porcelain'))
  if ($sporco.Count -gt 0) {
    Problema "$nome non e' pulito ($($sporco.Count) modifiche o file non tracciati; il primo: $($sporco[0])). Fai commit o metti da parte, poi riprova."
  } else { Ok "$nome pulito (nessuna modifica, nessun file non tracciato)" }
  $ramoAttuale = ([string](InvocaGit $percorso @('rev-parse', '--abbrev-ref', 'HEAD'))).Trim()
  if ($ramoAttuale -ne $Ramo) { Problema "$nome e' sul ramo '$ramoAttuale', serve '$Ramo'." } else { Ok "$nome e' su $Ramo" }
  $head = ([string](InvocaGit $percorso @('rev-parse', 'HEAD'))).Trim()
  $tagCommit = InvocaGitSilenzioso $percorso @('rev-parse', '--quiet', '--verify', "refs/tags/$Versione^{commit}")
  if (-not $tagCommit) {
    Problema "${nome}: il tag '$Versione' non esiste. Crealo prima (git tag $Versione) sul commit da consegnare."
  } else {
    $tagCommit = ([string]$tagCommit).Trim()
    if ($tagCommit -ne $head) {
      Problema "${nome}: il tag '$Versione' ($($tagCommit.Substring(0,10))) non e' il commit attuale di $Ramo ($($head.Substring(0,10)))."
    } else { Ok "tag $Versione = HEAD di $Ramo ($($head.Substring(0,10)))" }
  }
  $script:Commit[$nome] = $head
}

# ---------------------------------------------------------------- ricerca di segreti e dati reali
$NomiVietati = '^\.env($|\.)|\.(pem|key|p12|pfx|jks|keystore|sql|dump|sqlite|sqlite3|db|csv|bak)$|^id_(rsa|ed25519|ecdsa)|\.tar$|^uploads$'
$ModelliSegreto = @(
  '-----BEGIN [A-Z ]*PRIVATE KEY-----',
  '(?im)^[ \t]*(JWT_SECRET|NEXTAUTH_SECRET|AUTH_SECRET|REMEMBER_ME_KEY|DB_PASSWORD|POSTGRES_PASSWORD|ADMIN_PASSWORD|SMTP_PASSWORD|GOOGLE_CLIENT_SECRET|FACEBOOK_CLIENT_SECRET|BACKUP_PASSWORD|RESTIC_PASSWORD)[ \t]*[:=][ \t]*["'']?[A-Za-z0-9+/_\-]{6,}',
  'AKIA[0-9A-Z]{16}',
  'AIza[0-9A-Za-z_\-]{35}',
  'GOCSPX-[0-9A-Za-z_\-]{10,}',
  'gh[pousr]_[0-9A-Za-z]{30,}',
  'xox[abpr]-[0-9A-Za-z\-]{10,}'
)
$EstensioniBinarie = '\.(png|jpe?g|gif|ico|webp|woff2?|ttf|gz|zip|tar)$'

function CercaSegreti([string]$descrizione, [object[]]$file) {
  # ${file}: elenco di oggetti con Src (percorso reale) e Dest (nome nel pacchetto)
  $trovati = New-Object System.Collections.Generic.List[string]
  foreach ($f in $file) {
    $nome = Split-Path $f.Src -Leaf
    if ($nome -ne '.env.esempio' -and $nome -match $NomiVietati) {
      $trovati.Add("file vietato: $($f.Dest) ($nome)")
      continue
    }
    if ($f.Src -match $EstensioniBinarie) { continue }
    $testo = [System.IO.File]::ReadAllText($f.Src)
    foreach ($m in $ModelliSegreto) {
      if ($testo -match $m) { $trovati.Add("possibile segreto in $($f.Dest) (modello: $($m.Substring(0, [Math]::Min(40, $m.Length)))...)"); break }
    }
  }
  if ($trovati.Count -gt 0) {
    foreach ($t in $trovati) { Problema "SEGRETO/DATI REALI - $t" }
  } else { Ok "${descrizione}: nessun segreto, nessuna chiave, nessun file di dati reali ($($file.Count) file controllati)" }
}

# Tutti i file di una cartella sorgente (anche quelli che NON verrebbero copiati): un .env dimenticato li' dentro e' gia' un errore.
function FileDiCartelle([string[]]$cartelle) {
  $r = New-Object System.Collections.Generic.List[object]
  foreach ($c in $cartelle) {
    if (Test-Path $c) {
      Get-ChildItem $c -Recurse -File -Force | ForEach-Object { $r.Add([pscustomobject]@{ Dest = $_.FullName; Src = $_.FullName }) }
    }
  }
  return , $r.ToArray()
}

# Elenco dei file che entrano nel pacchetto: @{ Dest = percorso nel pacchetto; Src = file sorgente }
function MappaSorgenti([string]$radiceBe, [string]$radiceFe) {
  $c = Join-Path $radiceBe 'deploy\pacchetto\contenuto'
  $elenco = New-Object System.Collections.Generic.List[object]
  function Aggiungi($dest, $src) { $elenco.Add([pscustomobject]@{ Dest = $dest; Src = $src }) }
  Aggiungi 'docker-compose.prod.yml' (Join-Path $c 'docker-compose.prod.yml')
  Aggiungi 'Caddyfile'               (Join-Path $c 'Caddyfile')
  Aggiungi '.env.esempio'            (Join-Path $c '.env.esempio')
  Aggiungi 'INSTALLA.md'             (Join-Path $c 'INSTALLA.md')
  Aggiungi 'AGGIORNA.md'             (Join-Path $c 'AGGIORNA.md')
  Aggiungi 'PERSONALIZZARE.md'       (Join-Path $radiceFe 'PERSONALIZZARE.md')
  Aggiungi 'config-esempio/sito.json'  (Join-Path $c 'config\sito.json')
  Aggiungi 'config-esempio/testi.json' (Join-Path $c 'config\testi.json')
  $caddy = Join-Path $radiceBe 'deploy\caddy'
  Aggiungi 'caddy/abc-sito.caddy'   (Join-Path $caddy 'abc-sito.caddy')
  Aggiungi 'caddy/manutenzione.sh'  (Join-Path $caddy 'manutenzione.sh')
  $pagine = Join-Path $caddy 'pagine'
  if (Test-Path $pagine) {
    Get-ChildItem $pagine -File | Where-Object { $_.Name -ne 'MANUTENZIONE' } | ForEach-Object { Aggiungi ("caddy/pagine/" + $_.Name) $_.FullName }
  } else { Aggiungi 'caddy/pagine/sito-non-disponibile.html' (Join-Path $pagine 'sito-non-disponibile.html') }
  $script = Join-Path $c 'script'
  if (Test-Path $script) {
    Get-ChildItem $script -Filter *.sh -File | ForEach-Object { Aggiungi ("script/" + $_.Name) $_.FullName }
  } else { Aggiungi 'script/installa.sh' (Join-Path $script 'installa.sh') }
  return , $elenco.ToArray()
}

function ControllaPresenza([object[]]$mappa) {
  $mancano = @($mappa | Where-Object { -not (Test-Path $_.Src -PathType Leaf) })
  if ($mancano.Count -gt 0) {
    foreach ($m in $mancano) { Problema "file da consegnare mancante: $($m.Dest) (cercato in $($m.Src))" }
  } else { Ok "tutti i $($mappa.Count) file da consegnare esistono" }
  # script obbligatori
  foreach ($s in 'prepara-server', 'installa', 'aggiorna', 'torna-indietro', 'prova-ripristino', 'registro-cancellazioni', 'lib') {
    if (-not ($mappa | Where-Object { $_.Dest -eq "script/$s.sh" })) { Problema "manca lo script script/$s.sh" }
  }
}

# ---------------------------------------------------------------- scrittura file (sempre a capo Unix per testi)
function ScriviTesto([string]$percorso, [string]$testo) {
  $cartella = Split-Path $percorso -Parent
  if (-not (Test-Path $cartella)) { New-Item -ItemType Directory -Path $cartella -Force | Out-Null }
  [System.IO.File]::WriteAllText($percorso, $testo.Replace("`r`n", "`n"), $utf8SenzaBom)
}
function CopiaNelPacchetto([string]$src, [string]$destAssoluto) {
  $cartella = Split-Path $destAssoluto -Parent
  if (-not (Test-Path $cartella)) { New-Item -ItemType Directory -Path $cartella -Force | Out-Null }
  if ($src -match $EstensioniBinarie) { Copy-Item $src $destAssoluto -Force }
  else { ScriviTesto $destAssoluto ([System.IO.File]::ReadAllText($src)) }
}

# ---------------------------------------------------------------- elenco delle chiavi di testi.json / sito.json
# Il Referente non ha il codice di abc-fe: l'elenco delle chiavi (con i valori originali) si GENERA da
# lib/testi/it.ts e lib/testi/sito.ts e si mette nel pacchetto come CHIAVI-DISPONIBILI.md. Non si copia a mano.
function EstraiChiavi([string]$file, [string]$inizio) {
  if (-not (Test-Path $file -PathType Leaf)) { throw "Non trovo ${file}: non posso generare l'elenco delle chiavi." }
  $pila = New-Object System.Collections.Generic.List[string]
  $risultato = New-Object System.Collections.Generic.List[object]
  $dentro = $false
  $inElenco = $false
  $chiave = "(?:([A-Za-z_][A-Za-z0-9_]*)|'([^']+)')"
  foreach ($r in [System.IO.File]::ReadAllLines($file)) {
    if (-not $dentro) { if ($r -match $inizio) { $dentro = $true }; continue }
    if ($inElenco) { if ($r -match '^\s*\],?\s*(//.*)?$') { $inElenco = $false }; continue }
    if ($r -match '^\s*\},?\s*(//.*)?$') {
      if ($pila.Count -eq 0) { break }
      $pila.RemoveAt($pila.Count - 1)
      continue
    }
    if ($r -match "^\s*${chiave}:\s*\{\s*(//.*)?$") {
      $pila.Add($(if ($Matches[1]) { $Matches[1] } else { $Matches[2] }))
      continue
    }
    if ($r -match "^\s*${chiave}:\s*\[\s*(//.*)?$") {
      $nome = $(if ($Matches[1]) { $Matches[1] } else { $Matches[2] })
      $risultato.Add([pscustomobject]@{ Chiave = (($pila + $nome) -join '.'); Valore = '(elenco di paragrafi o voci: se lo cambi, riscrivilo per intero)' })
      $inElenco = $true
      continue
    }
    if ($r -match "^\s*${chiave}:\s*\[(.*)\],?\s*(//.*)?$") {
      $nome = $(if ($Matches[1]) { $Matches[1] } else { $Matches[2] })
      $risultato.Add([pscustomobject]@{ Chiave = (($pila + $nome) -join '.'); Valore = "(elenco) $($Matches[3])" })
      continue
    }
    if ($r -match "^\s*${chiave}:\s*(?:'((?:[^'\\]|\\.)*)'|""((?:[^""\\]|\\.)*)"")\s*,?\s*(//.*)?$") {
      $nome = $(if ($Matches[1]) { $Matches[1] } else { $Matches[2] })
      $valore = $(if ($null -ne $Matches[3]) { $Matches[3] } else { $Matches[4] })
      $risultato.Add([pscustomobject]@{ Chiave = (($pila + $nome) -join '.'); Valore = $valore.Replace("\'", "'") })
    }
  }
  return , $risultato.ToArray()
}

function ElencoChiaviMarkdown([string]$radiceFe) {
  $testi = EstraiChiavi (Join-Path $radiceFe 'lib\testi\it.ts') '^export const it\b.*=\s*\{\s*$'
  $sito = EstraiChiavi (Join-Path $radiceFe 'lib\testi\sito.ts') '^export const sitoPredefinito\b.*=\s*\{\s*$'
  if ($testi.Count -lt 100) { throw "Ho trovato solo $($testi.Count) chiavi in lib/testi/it.ts: il formato del file e' cambiato, aggiorna EstraiChiavi in crea-pacchetto.ps1." }
  if ($sito.Count -lt 8) { throw "Ho trovato solo $($sito.Count) chiavi in lib/testi/sito.ts: il formato del file e' cambiato, aggiorna EstraiChiavi in crea-pacchetto.ps1." }
  $m = New-Object System.Text.StringBuilder
  [void]$m.AppendLine('# Chiavi che si possono personalizzare')
  [void]$m.AppendLine('')
  [void]$m.AppendLine('Elenco GENERATO dal codice del sito (non scritto a mano), con i valori originali. Vedi PERSONALIZZARE.md per come usarlo.')
  [void]$m.AppendLine('')
  [void]$m.AppendLine('Nei file `testi.json` e `sito.json` le chiavi con il punto sono annidate: `auth.accedi` si scrive')
  [void]$m.AppendLine('`{ "auth": { "accedi": "Entra" } }`. I segnaposto come `{nome}` vanno lasciati dove sono.')
  [void]$m.AppendLine('')
  [void]$m.AppendLine('## sito.json (dati dell''associazione)')
  [void]$m.AppendLine('')
  foreach ($x in $sito) { [void]$m.AppendLine("- ``$($x.Chiave)``: $($x.Valore)") }
  [void]$m.AppendLine('')
  [void]$m.AppendLine('## testi.json (testi dell''interfaccia)')
  $sezione = ''
  foreach ($x in $testi) {
    $prima = $x.Chiave.Split('.')[0]
    if ($prima -ne $sezione) {
      $sezione = $prima
      [void]$m.AppendLine('')
      [void]$m.AppendLine("### $sezione")
      [void]$m.AppendLine('')
    }
    $v = $x.Valore
    if ($v.Length -gt 110) { $v = $v.Substring(0, 107) + '...' }
    [void]$m.AppendLine("- ``$($x.Chiave)``: $v")
  }
  return @{ Testo = $m.ToString(); Testi = $testi.Count; Sito = $sito.Count }
}

# PERSONALIZZARE.md nasce in abc-fe e parla del codice sorgente: nel pacchetto si correggono i riferimenti.
function AdattaPersonalizzare([string]$testo) {
  # Modelli a espressione regolare (il file e' in italiano con accenti; qui solo ASCII: '.' prende la lettera accentata).
  $sost = @(
    @('I valori originali sono in `lib/testi/it\.ts` \(testi\) e `lib/testi/sito\.ts` \(dati\): da l. si copiano\s+i nomi delle chiavi\.',
      'L''elenco di TUTTE le chiavi, con i valori originali, e'' nel file `CHIAVI-DISPONIBILI.md` (nella cartella del sito, `/opt/abc-sito`): da li'' si copiano i nomi.'),
    @('\(nel Docker: `docker compose restart frontend`\)', '(sul server: `bash /opt/abc-sito/script/riavvia.sh frontend`)'),
    @('`docker compose logs frontend`', '`bash /opt/abc-sito/script/registri.sh frontend`')
  )
  foreach ($x in $sost) {
    if (-not [regex]::IsMatch($testo, $x[0])) { throw "PERSONALIZZARE.md e' cambiato: non trovo il testo da correggere ($($x[0])). Aggiorna AdattaPersonalizzare in crea-pacchetto.ps1." }
    $testo = [regex]::Replace($testo, $x[0], $x[1].Replace('$', '$$'))
  }
  # il rimando al file dei codici di errore non e' nel pacchetto (se c'e' ancora, si toglie)
  return [regex]::Replace($testo, ' \(elenco in `abc-be/docs/codici-errore\.md`\)', '')
}

function ComprimiGzip([string]$origine, [string]$destinazione) {
  $in = [System.IO.File]::OpenRead($origine)
  try {
    $out = [System.IO.File]::Create($destinazione)
    try {
      $gz = New-Object System.IO.Compression.GZipStream($out, [System.IO.Compression.CompressionLevel]::Optimal)
      try { $in.CopyTo($gz) } finally { $gz.Dispose() }
    } finally { $out.Dispose() }
  } finally { $in.Dispose() }
}

# ================================================================ INIZIO
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if ($Versione -notmatch '^v\d+\.\d+\.\d+$') { throw "La versione deve avere la forma v1.2.3 (ricevuto: '$Versione')." }
if ($PSVersionTable.PSVersion.Major -lt 5) { throw "Serve PowerShell 5.1 o piu' recente." }

Write-Host ''
if ($ProvaASecco) {
  Write-Host "CREA-PACCHETTO $Versione  -  PROVA A SECCO: non costruisco e non scrivo niente, controllo e basta." -ForegroundColor Yellow
} else {
  Write-Host "CREA-PACCHETTO $Versione" -ForegroundColor Cyan
}
Info "abc-be   : $RepoBackend"
Info "abc-fe   : $RepoFrontend"
Info "uscita   : $CartellaUscita"
Info "zip      : $NomeZip"

# 1. strumenti
Titolo '1. Strumenti'
foreach ($p in 'git', 'tar') {
  if (Get-Command $p -ErrorAction SilentlyContinue) { Ok "$p presente" } else { Problema "Manca il programma '$p'." }
}
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $ErrorActionPreference = 'Continue'
  & docker version --format '{{.Server.Version}}' *> $null
  $codiceDocker = $LASTEXITCODE
  $ErrorActionPreference = 'Stop'
  if ($codiceDocker -eq 0) { Ok 'docker risponde' }
  elseif ($ProvaASecco) { Info 'docker non risponde adesso (in prova a secco non serve, per il pacchetto vero si)' }
  else { Problema "Docker Desktop non e' avviato." }
} else { Problema "Manca il programma 'docker'." }

# 2. repository
ControllaRepo 'abc-be' $RepoBackend
ControllaRepo 'abc-fe' $RepoFrontend

# 3. uscita fuori dai repository
Titolo '3. Cartella di uscita'
foreach ($r in @($RepoBackend, $RepoFrontend)) {
  $rr = $r.TrimEnd('\') + '\'
  if (($CartellaUscita.TrimEnd('\') + '\').StartsWith($rr, [System.StringComparison]::OrdinalIgnoreCase)) {
    Problema "La cartella di uscita ($CartellaUscita) sta dentro il repository ${r}: deve stare fuori."
  }
}
$zipFinale = Join-Path $CartellaUscita $NomeZip
if ((Test-Path $zipFinale) -and -not $Sovrascrivi) { Problema "Esiste gia' $zipFinale. Usa -Sovrascrivi per rifarlo." }
if ($script:Problemi.Count -eq 0 -or -not $ProvaASecco) { Ok "uscita: $zipFinale" }

# 4. file da consegnare e segreti (in prova a secco si guardano le cartelle di lavoro; nel pacchetto vero i file del tag)
Titolo '4. File da consegnare e ricerca di segreti'
$mappaProva = MappaSorgenti $RepoBackend $RepoFrontend
ControllaPresenza $mappaProva
$mappaEsistenti = @($mappaProva | Where-Object { Test-Path $_.Src -PathType Leaf })
CercaSegreti 'file da consegnare' $mappaEsistenti
CercaSegreti 'cartelle sorgente del pacchetto (contenuto/ e caddy/)' (FileDiCartelle @((Join-Path $RepoBackend 'deploy\pacchetto\contenuto'), (Join-Path $RepoBackend 'deploy\caddy')))
# l'elenco delle chiavi di personalizzazione si genera da abc-fe: se non si riesce, il pacchetto sarebbe incompleto
try {
  $chiaviProva = ElencoChiaviMarkdown $RepoFrontend
  Ok "elenco chiavi generabile da abc-fe ($($chiaviProva.Testi) testi, $($chiaviProva.Sito) dati del sito)"
} catch { Problema "elenco delle chiavi: $($_.Exception.Message)" }
# nessun .env o chiave nel codice dei due repo (anche tra i file ignorati da git)
foreach ($r in @(@('abc-be', $RepoBackend), @('abc-fe', $RepoFrontend))) {
  if (Test-Path $r[1]) {
    $strani = @(Get-ChildItem $r[1] -Force -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^\.env($|\.)' -and $_.Name -notin @('.env.example', '.env.esempio') })
    if ($strani.Count -gt 0) { Problema "$($r[0]): nella radice ci sono file .env ($($strani.Name -join ', ')). Il pacchetto parte dal tag e non li copia, ma toglili o spostali." }
    else { Ok "$($r[0]): nessun file .env nella radice" }
  }
}

# 5. cosa cambia rispetto alla versione precedente
Titolo '5. Versione precedente e database'
$precedente = $null
$migrazioniNuove = @()
$tagBe = InvocaGitSilenzioso $RepoBackend @('rev-parse', '--quiet', '--verify', "refs/tags/$Versione^{commit}")
if ($tagBe) {
  $prec = InvocaGitSilenzioso $RepoBackend @('describe', '--tags', '--abbrev=0', "$Versione^")
  if ($prec) {
    $precedente = ([string]$prec).Trim()
    $migrazioniNuove = @(InvocaGitSilenzioso $RepoBackend @('diff', '--name-only', '--diff-filter=A', "$precedente..$Versione", '--', 'src/main/resources/db/migration'))
    $migrazioniNuove = @($migrazioniNuove | Where-Object { $_ } | ForEach-Object { Split-Path $_ -Leaf })
    Ok "versione precedente: $precedente; migrazioni nuove: $(if ($migrazioniNuove.Count) { $migrazioniNuove -join ', ' } else { 'nessuna' })"
  } else { Info 'nessuna versione precedente (prima consegna): CAMBIA_DATABASE=si' }
} else { Info 'tag mancante: non posso calcolare cosa cambia' }
$cambiaDb = if (-not $precedente -or $migrazioniNuove.Count -gt 0) { 'si' } else { 'no' }

# ---------------------------------------------------------------- prova a secco: si ferma qui
if ($ProvaASecco) {
  Titolo '6. Cosa farei con il pacchetto vero (NIENTE di cio che segue viene eseguito adesso)'
  Simulo "creo la cartella di lavoro $Staging"
  Simulo "esporto il codice dei due repository DAL TAG $Versione in $Esportazioni (git archive)"
  Simulo "docker pull $ImmagineCaddy e $ImmaginePostgres"
  Simulo "docker build -> abc-musical/backend:$Versione (da abc-be, Dockerfile in radice)"
  Simulo "docker build -> abc-musical/frontend:$Versione (da abc-fe, SPRING_API_URL=http://backend:8080)"
  Simulo "docker build -> abc-musical/backup:$Versione (da deploy/pacchetto/contenuto, backup/Dockerfile)"
  Simulo "docker save + gzip -> immagini/abc-backend-$Versione.tar.gz, abc-frontend-$Versione.tar.gz, abc-backup-$Versione.tar.gz, caddy.tar.gz, postgres.tar.gz"
  Simulo "copio nel pacchetto ($($mappaProva.Count) file):"
  foreach ($m in $mappaProva) { Info "    $($m.Dest)" }
  Simulo "genero CHIAVI-DISPONIBILI.md da lib/testi/it.ts e sito.ts di abc-fe e correggo i rimandi di PERSONALIZZARE.md"
  Simulo "scrivo versione.env (VERSIONE=$Versione, CAMBIA_DATABASE=$cambiaDb), NOTE-DI-RILASCIO.md, MANIFEST.txt (versione, commit dei due repo, data)"
  Simulo "rifaccio la ricerca di segreti sul pacchetto assemblato (compresi gli script e i documenti)"
  Simulo "calcolo gli hash SHA-256 di tutti i file -> SHA256SUMS.txt"
  Simulo "creo $zipFinale (cartella interna $NomePacchetto/) e $zipFinale.sha256.txt"
  Simulo "cancello la cartella di lavoro e i sorgenti esportati: restano solo lo zip e la sua impronta"
  Write-Host ''
  if ($script:Problemi.Count -gt 0) {
    Write-Host "PROVA A SECCO: $($script:Problemi.Count) PROBLEMI da risolvere prima del pacchetto vero:" -ForegroundColor Red
    $script:Problemi | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
  }
  Write-Host 'PROVA A SECCO: nessun problema trovato. Il pacchetto vero si puo'' creare (stesso comando senza -ProvaASecco).' -ForegroundColor Green
  exit 0
}

# ================================================================ PACCHETTO VERO
$inizio = Get-Date
$ripulisci = $true
try {
  Titolo '6. Preparo le cartelle'
  if (-not (Test-Path $CartellaUscita)) { New-Item -ItemType Directory -Path $CartellaUscita -Force | Out-Null }
  foreach ($d in $Staging, $Esportazioni) {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    New-Item -ItemType Directory -Path $d -Force | Out-Null
  }
  $radice = Join-Path $Staging $NomePacchetto
  New-Item -ItemType Directory -Path (Join-Path $radice 'immagini') -Force | Out-Null

  Titolo '7. Esporto il codice dal tag (solo cio che e'' nel commit, niente file ignorati)'
  $srcBe = Join-Path $Esportazioni 'abc-be'
  $srcFe = Join-Path $Esportazioni 'abc-fe'
  foreach ($x in @(@('abc-be', $RepoBackend, $srcBe), @('abc-fe', $RepoFrontend, $srcFe))) {
    $tarFile = Join-Path $Esportazioni ($x[0] + '.tar')
    New-Item -ItemType Directory -Path $x[2] -Force | Out-Null
    InvocaGit $x[1] @('archive', '--format=tar', '-o', $tarFile, $Versione) | Out-Null
    $ErrorActionPreference = 'Continue'
    & tar -xf $tarFile -C $x[2]
    $codiceTar = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($codiceTar -ne 0) { throw "tar non e' riuscito a estrarre $tarFile" }
    Remove-Item $tarFile -Force
    Ok "$($x[0]) esportato dal tag $Versione"
  }

  Titolo '8. Controllo segreti sui file esportati'
  $mappa = MappaSorgenti $srcBe $srcFe
  ControllaPresenza $mappa
  CercaSegreti 'file da consegnare (dal tag)' $mappa
  CercaSegreti 'cartelle sorgente del pacchetto (dal tag)' (FileDiCartelle @((Join-Path $srcBe 'deploy\pacchetto\contenuto'), (Join-Path $srcBe 'deploy\caddy')))
  $tuttiEsportati = @(Get-ChildItem $Esportazioni -Recurse -File -Force | Where-Object { $_.Name -match '^\.env($|\.)' -and $_.Name -notin @('.env.example', '.env.esempio') })
  if ($tuttiEsportati.Count -gt 0) { Problema "Nel codice del tag ci sono file .env: $($tuttiEsportati.FullName -join ', ')" } else { Ok 'nessun file .env nel codice del tag' }

  Titolo '9. Costruisco le immagini docker (puo'' richiedere parecchi minuti)'
  Esegui "scarico $ImmagineCaddy" { & docker pull $ImmagineCaddy }
  Esegui "scarico $ImmaginePostgres" { & docker pull $ImmaginePostgres }
  Esegui "backend $Versione" { & docker build -t "abc-musical/backend:$Versione" $srcBe }
  Esegui "frontend $Versione" { & docker build --build-arg SPRING_API_URL=http://backend:8080 --build-arg BACKEND_HOSTNAME=backend:8080 --build-arg "CACHEBUST=$Versione" -t "abc-musical/frontend:$Versione" $srcFe }
  $contestoBackup = Join-Path $srcBe 'deploy\pacchetto\contenuto'
  Esegui "backup $Versione" { & docker build -f (Join-Path $contestoBackup 'backup\Dockerfile') -t "abc-musical/backup:$Versione" $contestoBackup }

  Titolo '10. Salvo le immagini in file compressi'
  $immagini = @(
    @{ Nome = "abc-musical/backend:$Versione";  File = "abc-backend-$Versione.tar.gz" },
    @{ Nome = "abc-musical/frontend:$Versione"; File = "abc-frontend-$Versione.tar.gz" },
    @{ Nome = "abc-musical/backup:$Versione";   File = "abc-backup-$Versione.tar.gz" },
    @{ Nome = $ImmagineCaddy;                   File = 'caddy.tar.gz' },
    @{ Nome = $ImmaginePostgres;                File = 'postgres.tar.gz' }
  )
  $righeImmagini = @()
  foreach ($i in $immagini) {
    $tarTmp = Join-Path $Staging ($i.File + '.tar')
    Esegui "docker save $($i.Nome)" { & docker save -o $tarTmp $i.Nome }
    ComprimiGzip $tarTmp (Join-Path $radice ('immagini\' + $i.File))
    Remove-Item $tarTmp -Force
    $id = ([string](& docker image inspect --format '{{.Id}}' $i.Nome)).Trim()
    $righeImmagini += "  $($i.Nome)  $id  immagini/$($i.File)"
    Ok "$($i.File)"
  }

  Titolo '11. Copio i file nel pacchetto'
  foreach ($m in $mappa) { CopiaNelPacchetto $m.Src (Join-Path $radice ($m.Dest.Replace('/', '\'))) }
  Ok "$($mappa.Count) file copiati"
  $chiavi = ElencoChiaviMarkdown $srcFe
  ScriviTesto (Join-Path $radice 'CHIAVI-DISPONIBILI.md') $chiavi.Testo
  $fileP = Join-Path $radice 'PERSONALIZZARE.md'
  ScriviTesto $fileP (AdattaPersonalizzare ([System.IO.File]::ReadAllText($fileP)))
  Ok "CHIAVI-DISPONIBILI.md generato ($($chiavi.Testi) testi, $($chiavi.Sito) dati del sito); PERSONALIZZARE.md adattato al pacchetto"
  ScriviTesto (Join-Path $radice 'versione.env') ("VERSIONE=$Versione`nCAMBIA_DATABASE=$cambiaDb`nDATA_PACCHETTO=$($Data.ToString('yyyy-MM-dd'))`n")

  # note di rilascio
  $note = New-Object System.Text.StringBuilder
  [void]$note.AppendLine("# Note di rilascio - versione $Versione")
  [void]$note.AppendLine('')
  [void]$note.AppendLine("Data del pacchetto: $($Data.ToString('dd/MM/yyyy'))" + $(if ($precedente) { " - versione precedente: $precedente" } else { ' - prima consegna' }))
  [void]$note.AppendLine('')
  if ($cambiaDb -eq 'si' -and $precedente) {
    [void]$note.AppendLine("**ATTENZIONE: questa versione CAMBIA IL DATABASE** (migrazioni nuove: $($migrazioniNuove -join ', ')).")
    [void]$note.AppendLine('**Tornare indietro vuol dire ripristinare la copia fatta da aggiorna.sh: gli iscritti e i contenuti inseriti DOPO l''aggiornamento andrebbero persi.**')
    [void]$note.AppendLine('')
  } elseif ($precedente) {
    [void]$note.AppendLine('Questa versione non cambia il database: tornare indietro non fa perdere dati.')
    [void]$note.AppendLine('')
  }
  if ($NoteDiRilascio) {
    if (-not (Test-Path $NoteDiRilascio)) { throw "File delle note non trovato: $NoteDiRilascio" }
    [void]$note.AppendLine([System.IO.File]::ReadAllText((Resolve-Path $NoteDiRilascio).Path))
  } else {
    [void]$note.AppendLine('## Cosa cambia (generato dai messaggi dei commit)')
    foreach ($x in @(@('Sito (abc-fe)', $RepoFrontend), @('Server (abc-be)', $RepoBackend))) {
      [void]$note.AppendLine('')
      [void]$note.AppendLine("### $($x[0])")
      $righe = if ($precedente) { InvocaGitSilenzioso $x[1] @('log', '--no-merges', '--pretty=format:- %s', "$precedente..$Versione") } else { $null }
      if ($righe) { foreach ($r in @($righe) | Select-Object -First 80) { [void]$note.AppendLine($r) } } else { [void]$note.AppendLine('- (nessun elenco disponibile)') }
    }
  }
  ScriviTesto (Join-Path $radice 'NOTE-DI-RILASCIO.md') $note.ToString()

  # manifest
  $man = New-Object System.Text.StringBuilder
  [void]$man.AppendLine('ABC Musical Company - pacchetto di installazione del sito')
  [void]$man.AppendLine("versione:              $Versione")
  [void]$man.AppendLine("creato il:             $($Data.ToString('yyyy-MM-dd HH:mm:ss')) (ora del PC di chi ha creato il pacchetto)")
  [void]$man.AppendLine("commit abc-be:         $($script:Commit['abc-be'])")
  [void]$man.AppendLine("commit abc-fe:         $($script:Commit['abc-fe'])")
  [void]$man.AppendLine("versione precedente:   $(if ($precedente) { $precedente } else { 'nessuna (prima consegna)' })")
  [void]$man.AppendLine("cambia il database:    $cambiaDb")
  [void]$man.AppendLine('immagini (nome, id, file):')
  foreach ($r in $righeImmagini) { [void]$man.AppendLine($r) }
  [void]$man.AppendLine('hash di ogni file:      vedi SHA256SUMS.txt (si controllano con: sha256sum -c SHA256SUMS.txt)')
  ScriviTesto (Join-Path $radice 'MANIFEST.txt') $man.ToString()

  Titolo '12. Ultimo controllo segreti sul pacchetto assemblato'
  $assemblati = @(Get-ChildItem $radice -Recurse -File -Force | ForEach-Object {
      [pscustomobject]@{ Dest = $_.FullName.Substring($radice.Length + 1); Src = $_.FullName } })
  CercaSegreti 'pacchetto assemblato' ($assemblati | Where-Object { $_.Dest -notlike 'immagini*' })

  Titolo '13. Hash SHA-256 di tutti i file'
  $righeHash = New-Object System.Collections.Generic.List[string]
  foreach ($f in (Get-ChildItem $radice -Recurse -File -Force | Sort-Object FullName)) {
    $rel = $f.FullName.Substring($radice.Length + 1).Replace('\', '/')
    if ($rel -eq 'SHA256SUMS.txt') { continue }
    $h = (Get-FileHash -Algorithm SHA256 $f.FullName).Hash.ToLowerInvariant()
    $righeHash.Add("$h  $rel")
  }
  ScriviTesto (Join-Path $radice 'SHA256SUMS.txt') (($righeHash -join "`n") + "`n")
  Ok "$($righeHash.Count) file con hash"

  Titolo '14. Creo lo zip'
  if (Test-Path $zipFinale) { Remove-Item $zipFinale -Force }
  $zip = [System.IO.Compression.ZipFile]::Open($zipFinale, [System.IO.Compression.ZipArchiveMode]::Create)
  try {
    foreach ($f in (Get-ChildItem $radice -Recurse -File -Force | Sort-Object FullName)) {
      $rel = $f.FullName.Substring($radice.Length + 1).Replace('\', '/')
      $entry = $zip.CreateEntry("$NomePacchetto/$rel", [System.IO.Compression.CompressionLevel]::Optimal)
      try {   # permessi Unix: eseguibile per gli script (se la versione di .NET lo permette; INSTALLA.md usa comunque "bash script/...")
        $modo = if ($rel -like '*.sh') { 0x81ED } else { 0x81A4 }
        $entry.ExternalAttributes = ($modo -shl 16)
      } catch { }
      $s = $entry.Open()
      try { $in = [System.IO.File]::OpenRead($f.FullName); try { $in.CopyTo($s) } finally { $in.Dispose() } } finally { $s.Dispose() }
    }
  } finally { $zip.Dispose() }
  $hashZip = (Get-FileHash -Algorithm SHA256 $zipFinale).Hash.ToLowerInvariant()
  ScriviTesto "$zipFinale.sha256.txt" "$hashZip  $NomeZip`n"
  $dimensione = [Math]::Round((Get-Item $zipFinale).Length / 1MB, 1)

  Titolo '15. Pulizia'
  foreach ($d in $Staging, $Esportazioni) {
    if ($d.StartsWith($CartellaUscita, [System.StringComparison]::OrdinalIgnoreCase) -and (Test-Path $d)) { Remove-Item $d -Recurse -Force }
  }
  Ok 'cartella di lavoro e sorgenti esportati cancellati'
  $ripulisci = $false

  $durata = [Math]::Round(((Get-Date) - $inizio).TotalMinutes, 1)
  Write-Host ''
  Write-Host "PACCHETTO PRONTO ($durata minuti)" -ForegroundColor Green
  Write-Host "  zip      : $zipFinale ($dimensione MB)"
  Write-Host "  SHA-256  : $hashZip"
  Write-Host "  impronta : $zipFinale.sha256.txt"
  Write-Host "  Consegna ad ABC lo zip E l'impronta SHA-256 (anche per telefono o messaggio): vedi LEGGIMI-FEDERICO.md."
}
catch {
  Write-Host ''
  Write-Host "ERRORE: $($_.Exception.Message)" -ForegroundColor Red
  if ($ripulisci -and (Test-Path $CartellaUscita)) {
    Write-Host 'Il pacchetto NON e'' stato creato. Cartella di lavoro lasciata per capire cosa e'' successo:' -ForegroundColor Red
    Write-Host "  $Staging"
    Write-Host "  $Esportazioni"
  }
  exit 1
}
