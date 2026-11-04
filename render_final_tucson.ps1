Add-Type -AssemblyName System.Drawing

function Load-BitmapNoLock([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $ms = New-Object System.IO.MemoryStream(,$bytes)
    return [System.Drawing.Bitmap]::FromStream($ms)
}

function Copy-WheelCircle {
    param(
        [System.Drawing.Bitmap]$src600,
        [System.Drawing.Graphics]$dstG,
        [int]$cx,
        [int]$cy,
        [int]$r
    )
    $x = $cx - $r
    $y = $cy - $r
    $diam = $r * 2
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse($x, $y, $diam, $diam)
    $dstG.SetClip($path)
    $rect = New-Object System.Drawing.Rectangle($x, $y, $diam, $diam)
    $dstG.DrawImage($src600, $rect, $rect, [System.Drawing.GraphicsUnit]::Pixel)
    $dstG.ResetClip()
    $path.Dispose()
}

$stickerLeft = Load-BitmapNoLock "D:/workspaces/general/jh-launcher/sticker_trans_left.png"
$stickerRight = Load-BitmapNoLock "D:/workspaces/general/jh-launcher/sticker_trans_right.png"

# =========================================================================
# 1. SIDE VIEW
# =========================================================================
Write-Output "Processing SIDE VIEW..."
$sideOrig = Load-BitmapNoLock "D:/workspaces/general/jh-launcher/assets/tucson2013_black_side.original.jpg"
$side5SpokeRaw = Load-BitmapNoLock "C:/Users/joseh/.gemini/antigravity/brain/2a9baabe-d1ba-41af-b720-d49eb2161b73/tucson2013_side_corrected_sticker_v2_1793311220586.jpg"

$side5Spoke600 = New-Object System.Drawing.Bitmap(600, 335)
$gs = [System.Drawing.Graphics]::FromImage($side5Spoke600)
$gs.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gs.DrawImage($side5SpokeRaw, 0, 0, 600, 335)
$gs.Dispose()
$side5SpokeRaw.Dispose()

$finalSide = New-Object System.Drawing.Bitmap($sideOrig)
$gSide = [System.Drawing.Graphics]::FromImage($finalSide)
$gSide.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Wheels (cx=138, cx=471, cy=224, r=42)
Copy-WheelCircle -src600 $side5Spoke600 -dstG $gSide -cx 138 -cy 224 -r 42
Copy-WheelCircle -src600 $side5Spoke600 -dstG $gSide -cx 471 -cy 224 -r 42
$side5Spoke600.Dispose()
$sideOrig.Dispose()

# Sticker: Walking LEFT (car front is LEFT)
# Door seam is at x=333. Sticker cut is at 41% of 44 = 18px.
# Sticker left = 333 - 18 = 315.
$gSide.DrawImage($stickerLeft, 315, 138, 44, 70)
$gSide.Dispose()

$finalSide.Save("D:/workspaces/general/jh-launcher/assets/tucson2013_black_side.jpg", [System.Drawing.Imaging.ImageFormat]::Jpeg)
$finalSide.Dispose()
Write-Output "SIDE VIEW SAVED."

# =========================================================================
# 2. FRONT 3/4 VIEW
# =========================================================================
Write-Output "Processing FRONT VIEW..."
$frontOrig = Load-BitmapNoLock "D:/workspaces/general/jh-launcher/assets/tucson2013_black_front.original.jpg"
$front5SpokeRaw = Load-BitmapNoLock "C:/Users/joseh/.gemini/antigravity/brain/2a9baabe-d1ba-41af-b720-d49eb2161b73/tucson2013_black_front_1793310253091.jpg"

$front5Spoke600 = New-Object System.Drawing.Bitmap(600, 335)
$gf = [System.Drawing.Graphics]::FromImage($front5Spoke600)
$gf.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gf.DrawImage($front5SpokeRaw, 0, 0, 600, 335)
$gf.Dispose()
$front5SpokeRaw.Dispose()

$finalFront = New-Object System.Drawing.Bitmap($frontOrig)
$gFront = [System.Drawing.Graphics]::FromImage($finalFront)
$gFront.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Wheels: Front wheel (right) cx=303, cy=228, r=43; Rear wheel (left) cx=125, cy=210, r=28
Copy-WheelCircle -src600 $front5Spoke600 -dstG $gFront -cx 303 -cy 228 -r 43
Copy-WheelCircle -src600 $front5Spoke600 -dstG $gFront -cx 125 -cy 210 -r 28
$front5Spoke600.Dispose()
$frontOrig.Dispose()

# Sticker: Walking RIGHT (car front is RIGHT)
# Door seam alignment at x=160
$gFront.DrawImage($stickerRight, 160, 140, 32, 53)
$gFront.Dispose()

$finalFront.Save("D:/workspaces/general/jh-launcher/assets/tucson2013_black_front.jpg", [System.Drawing.Imaging.ImageFormat]::Jpeg)
$finalFront.Dispose()
Write-Output "FRONT VIEW SAVED."

# =========================================================================
# 3. REAR 3/4 VIEW
# =========================================================================
Write-Output "Processing REAR VIEW..."
$rearOrig = Load-BitmapNoLock "D:/workspaces/general/jh-launcher/assets/tucson2013_black_rear.original.jpg"
$rear5SpokeRaw = Load-BitmapNoLock "C:/Users/joseh/.gemini/antigravity/brain/2a9baabe-d1ba-41af-b720-d49eb2161b73/tucson2013_black_rear_1793310284959.jpg"

$rear5Spoke600 = New-Object System.Drawing.Bitmap(600, 335)
$gr = [System.Drawing.Graphics]::FromImage($rear5Spoke600)
$gr.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gr.DrawImage($rear5SpokeRaw, 0, 0, 600, 335)
$gr.Dispose()
$rear5SpokeRaw.Dispose()

$finalRear = New-Object System.Drawing.Bitmap($rearOrig)
$gRear = [System.Drawing.Graphics]::FromImage($finalRear)
$gRear.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

# Wheels: Rear wheel (left) cx=320, cy=225, r=42; Front wheel (right) cx=480, cy=200, r=28
Copy-WheelCircle -src600 $rear5Spoke600 -dstG $gRear -cx 320 -cy 225 -r 42
Copy-WheelCircle -src600 $rear5Spoke600 -dstG $gRear -cx 480 -cy 200 -r 28
$rear5Spoke600.Dispose()
$rearOrig.Dispose()

# Sticker: Walking RIGHT (car front is RIGHT)
# Door seam is at x=405. Sticker cut is at 59% of 32 = 19px.
# Sticker left = 405 - 19 = 386.
$gRear.DrawImage($stickerRight, 386, 140, 32, 53)
$gRear.Dispose()

$finalRear.Save("D:/workspaces/general/jh-launcher/assets/tucson2013_black_rear.jpg", [System.Drawing.Imaging.ImageFormat]::Jpeg)
$finalRear.Dispose()
Write-Output "REAR VIEW SAVED."

$stickerLeft.Dispose()
$stickerRight.Dispose()
Write-Output "ALL THREE VIEWS RENDERED WITH EXACT SEAM ALIGNMENT."
