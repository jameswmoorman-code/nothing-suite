#!/usr/bin/env python3
"""
Dots Icons generator. Run from android-app/:  python3 dots-icons/tools_gen_icons.py

Renders every icon as a PNG (black rounded tile, white round dots) and writes
the icon-pack XML that launchers read. Re-run after editing PICTOGRAMS or
APPS; everything under res/ that this script owns is regenerated.

  res/drawable-nodpi/p_<name>.png   pictograms (11×11 dot grids below)
  res/drawable-nodpi/l_<char>.png   letter / digit icons (5×7 font from core-design Matrix.kt)
  res/drawable-nodpi/iconback.png   plain tile, used for apps we don't know
  res/xml/appfilter.xml, res/xml/drawable.xml, res/values/iconpack.xml, assets/appfilter.xml
"""
import os, re, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "src/main/res")
SIZE = 256            # px; launchers scale down
DOT_GRID = 13         # 13 dot cells across the tile (11 used + 1 margin each side)
BLACK = (0, 0, 0, 255); WHITE = (255, 255, 255, 255); RED = (215, 25, 33, 255)

# ---------------------------------------------------------------- font ----
def load_font():
    """Parse the 5×7 glyphs out of core-design's Matrix.kt so there is one source of truth."""
    src = open(os.path.join(HERE, "../core-design/src/main/java/uk/nothingsuite/design/glyph/Matrix.kt")).read()
    font = {}
    for m in re.finditer(r"g\('(.)',\s*((?:\"[.#]{5}\",?\s*){7})\)", src):
        rows = re.findall(r'"([.#]{5})"', m.group(2))
        font[m.group(1)] = rows
    return font

# ------------------------------------------------------------ pictograms ----
# 11×11, '#' = lit dot. Keep them chunky: a dot icon is read at 48 dp.
P = {}
def pic(name, grid): P[name] = [r for r in grid.strip("\n").split("\n")]

pic("phone", """
...........
.##........
.###.......
..###......
...###.....
....###....
.....###...
......###..
.......###.
........##.
...........""")
pic("message", """
...........
.#########.
#.........#
#.........#
#.........#
#.........#
.#########.
....#......
...#.......
..#........
...........""")
pic("camera", """
...........
...........
...###.....
.#########.
#....#....#
#...###...#
#...###...#
#....#....#
.#########.
...........
...........""")
pic("gallery", """
...........
.#########.
.#.......#.
.#..##...#.
.#..##...#.
.#......##.
.#....####.
.#..######.
.#########.
...........
...........""")
pic("settings", """
....#.#....
...#####...
..#.....#..
##..###..##
#...#.#...#
#...###...#
##.......##
..#.....#..
...#####...
....#.#....
...........""")
pic("clock", """
...........
...#####...
..#.....#..
.#...#...#.
.#...#...#.
.#...###.#.
.#.......#.
..#.....#..
...#####...
...........
...........""")
pic("calendar", """
...#...#...
.#########.
.#.......#.
.#########.
.#.#.#.#.#.
.#.......#.
.#.#.#.#.#.
.#.......#.
.#########.
...........
...........""")
pic("calculator", """
...........
..#######..
..#######..
..#.....#..
..#.#.#.#..
..#.....#..
..#.#.#.#..
..#.....#..
..#######..
...........
...........""")
pic("browser", """
...........
...#####...
..#..#..#..
.#...#...#.
.#########.
.#...#...#.
.#...#...#.
..#..#..#..
...#####...
...........
...........""")
pic("map", """
...........
....###....
...#...#...
..#.....#..
..#..#..#..
..#.....#..
...#...#...
....#.#....
.....#.....
...........
...........""")
pic("mail", """
...........
...........
#.........#
##.......##
#.#.....#.#
#..#...#..#
#...#.#...#
#....#....#
#.........#
###########
...........""")
pic("music", """
...........
.....#####.
.....#....#
.....#....#
.....#....#
.....#....#
.....#....#
..####.....
.#####.....
..###......
...........""")
pic("play", """
...........
...#.......
...##......
...###.....
...####....
...#####...
...####....
...###.....
...##......
...#.......
...........""")
pic("files", """
...........
.####......
.#...#.....
.#########.
.#.......#.
.#.......#.
.#.......#.
.#.......#.
.#########.
...........
...........""")
pic("weather", """
.....#.....
.#...#...#.
..#.....#..
....###....
...#####...
#..#####..#
...#####...
....###....
..#.....#..
.#...#...#.
.....#.....""")
pic("notes", """
...........
.#########.
.#.......#.
.#.#####.#.
.#.......#.
.#.#####.#.
.#.......#.
.#.###...#.
.#.......#.
.#########.
...........""")
pic("shop", """
...........
....###....
...#...#...
.#########.
.#.......#.
.#.......#.
.#.......#.
.#.......#.
.#########.
...........
...........""")
pic("bank", """
...........
.....#.....
...#####...
.#########.
.#.#.#.#.#.
.#.#.#.#.#.
.#.#.#.#.#.
.#.#.#.#.#.
.#########.
###########
...........""")
pic("card", """
...........
...........
.#########.
.#.......#.
.#########.
.#########.
.#.......#.
.#.###...#.
.#########.
...........
...........""")
pic("heart", """
...........
..##...##..
.####.####.
###########
###########
.#########.
..#######..
...#####...
....###....
.....#.....
...........""")
pic("video", """
...........
...........
.#######...
.#.....#.#.
.#.....##..
.#.....##..
.#.....#.#.
.#######...
...........
...........
...........""")
pic("bubble", """
...........
..#######..
.#.......#.
#.........#
#..#.#.#..#
#.........#
.#.......#.
..###.###..
....#.#....
....##.....
...........""")
pic("send", """
...........
...........
#####......
.#...###...
..#.....##.
...########
..#.....##.
.#...###...
#####......
...........
...........""")
pic("game", """
...........
...........
..#######..
.#.#.....#.
###.#.#.#.#
.#.#..#..#.
..#.....#..
..#.....#..
...........
...........
...........""")
pic("car", """
...........
...........
...#####...
..#.....#..
.#########.
#.........#
#.###.###.#
###########
.#.......#.
...........
...........""")
pic("food", """
...........
.#.#.#...#.
.#.#.#..#..
.#.#.#..#..
.#####..#..
..###..###.
...#....#..
...#....#..
...#....#..
...#....#..
...........""")
pic("parcel", """
...........
....###....
..##...##..
##.......##
#.##...##.#
#...###...#
#....#....#
##...#...##
..##.#.##..
....###....
...........""")
pic("train", """
...........
..#######..
.#.......#.
.#.#####.#.
.#.......#.
.#########.
.#.#...#.#.
.#########.
..#.....#..
.#.......#.
...........""")
pic("cloud", """
...........
...........
....####...
...######..
.#########.
###########
###########
.#########.
...........
...........
...........""")
pic("key", """
...........
.....###...
....#...#..
....#...#..
.....###...
......#....
......#....
....###....
......#....
....###....
...........""")
pic("photo", """
...........
...........
...#####...
..#.....#..
.#..###..#.
.#.#...#.#.
.#..###..#.
..#.....#..
...#####...
...........
...........""")
pic("fitness", """
...........
...........
.#.......#.
##.......##
##.#####.##
###########
##.#####.##
##.......##
.#.......#.
...........
...........""")
pic("book", """
...........
.####.####.
#....#....#
#....#....#
#....#....#
#....#....#
#....#....#
#....#....#
.####.####.
...........
...........""")
pic("news", """
...........
.#########.
.#.......#.
.#.##.##.#.
.#.##....#.
.#....##.#.
.#.##.##.#.
.#.......#.
.#########.
...........
...........""")
pic("home", """
...........
.....#.....
....###....
...#####...
..#######..
.#########.
..#.....#..
..#.###.#..
..#.#.#.#..
..#.###.#..
...........""")
pic("lock", """
...........
...#####...
..#.....#..
..#.....#..
.#########.
.#.......#.
.#...#...#.
.#...#...#.
.#.......#.
.#########.
...........""")
pic("tv", """
...........
.#########.
.#.......#.
.#.......#.
.#.......#.
.#.......#.
.#########.
.....#.....
...#####...
...........
...........""")
pic("pound", """
...........
....####...
...#....#..
...#.......
.#####.....
...#.......
...#.......
...#.......
..#######..
...........
...........""")
pic("chart", """
...........
#..........
#..........
#.......#..
#...#...#..
#...#.#.#..
#.#.#.#.#..
#.#.#.#.#..
#.#.#.#.#..
###########
...........""")
pic("mic", """
...........
....###....
....###....
....###....
....###....
..#.###.#..
..#.....#..
...#####...
.....#.....
...#####...
...........""")
pic("glyph", """
...........
....###....
..#.....#..
.#..###..#.
.#.#...#.#.
#..#.#.#..#
.#.#...#.#.
.#..###..#.
..#.....#..
....###....
...........""")
pic("dots", """
...........
...........
..#..#..#..
...........
...........
..#..#..#..
...........
...........
..#..#..#..
...........
...........""")

# ---------------------------------------------------------- package map ----
# package → pictogram name, or a single character for a letter icon.
# Pictograms first (recognisable), then letters for the long tail.
APPS = {
    # phone basics
    "com.android.dialer": "phone", "com.google.android.dialer": "phone", "com.nothing.dialer": "phone", "com.samsung.android.dialer": "phone",
    "com.android.contacts": "l:C", "com.google.android.contacts": "l:C", "com.samsung.android.app.contacts": "l:C",
    "com.android.mms": "message", "com.google.android.apps.messaging": "message", "com.samsung.android.messaging": "message",
    "com.android.camera": "camera", "com.android.camera2": "camera", "com.google.android.GoogleCamera": "camera", "com.nothing.camera": "camera", "com.sec.android.app.camera": "camera",
    "com.google.android.apps.photos": "photo", "com.nothing.gallery": "gallery", "com.android.gallery3d": "gallery", "com.sec.android.gallery3d": "gallery", "com.simplemobiletools.gallery.pro": "gallery",
    "com.android.settings": "settings", "com.nothing.launcher": "home", "com.nothing.launcher.wallpaper": "photo",
    "com.google.android.deskclock": "clock", "com.android.deskclock": "clock", "com.sec.android.app.clockpackage": "clock", "com.nothing.clock": "clock",
    "com.google.android.calendar": "calendar", "com.android.calendar": "calendar", "com.samsung.android.calendar": "calendar", "com.simplemobiletools.calendar.pro": "calendar", "com.microsoft.office.outlook": "mail",
    "com.google.android.calculator": "calculator", "com.android.calculator2": "calculator", "com.sec.android.app.popupcalculator": "calculator", "com.nothing.calculator": "calculator",
    "com.android.chrome": "browser", "org.mozilla.firefox": "browser", "com.brave.browser": "browser", "com.microsoft.emmx": "browser", "com.opera.browser": "browser", "com.duckduckgo.mobile.android": "browser", "com.sec.android.app.sbrowser": "browser", "com.vivaldi.browser": "browser", "org.chromium.chrome": "browser", "com.kiwibrowser.browser": "browser",
    "com.google.android.apps.maps": "map", "com.waze": "map", "com.citymapper.app.release": "map", "com.here.app.maps": "map", "net.osmand": "map", "com.google.android.apps.mapslite": "map",
    "com.google.android.gm": "mail", "com.google.android.gm.lite": "mail", "com.microsoft.office.outlook.lite": "mail", "com.samsung.android.email.provider": "mail", "ch.protonmail.android": "mail", "com.yahoo.mobile.client.android.mail": "mail", "me.bluemail.mail": "mail", "com.fsck.k9": "mail", "app.k9mail": "mail",
    "com.spotify.music": "music", "com.google.android.apps.youtube.music": "music", "com.apple.android.music": "music", "com.amazon.mp3": "music", "deezer.android.app": "music", "com.soundcloud.android": "music", "com.aspiro.tidal": "music", "com.shazam.android": "music", "org.videolan.vlc": "play", "com.maxmpz.audioplayer": "music", "com.nothing.musicplayer": "music", "com.samsung.android.app.music": "music", "com.spotify.lite": "music",
    "com.google.android.youtube": "play", "com.netflix.mediaclient": "video", "com.amazon.avod.thirdpartyclient": "video", "com.disney.disneyplus": "video", "uk.co.bbc.iplayer": "tv", "air.ITVMobilePlayer": "tv", "com.channel4.ondemand": "tv", "com.sky.skyplus": "tv", "com.nowtv.mobile": "tv", "tv.twitch.android.app": "video", "com.plexapp.android": "play", "com.google.android.videos": "play", "com.apple.atve.androidtv.appletv": "tv", "com.dazn": "tv", "com.mubi": "video", "com.paramountplus.android": "video", "com.google.android.apps.tv.launcherx": "tv",
    "com.google.android.apps.nbu.files": "files", "com.android.documentsui": "files", "com.google.android.documentsui": "files", "com.sec.android.app.myfiles": "files", "com.mi.android.globalFileexplorer": "files", "dev.dworks.apps.anexplorer.pro": "files", "com.lonelycatgames.Xplore": "files", "nextapp.fx": "files", "pl.solidexplorer2": "files",
    "com.google.android.apps.docs": "files", "com.dropbox.android": "cloud", "com.microsoft.skydrive": "cloud", "com.google.android.apps.drive": "cloud", "com.box.android": "cloud", "com.nextcloud.client": "cloud", "mega.privacy.android.app": "cloud", "com.pcloud.pcloud": "cloud",
    "com.google.android.apps.weather": "weather", "com.nothing.weather": "weather", "com.accuweather.android": "weather", "uk.gov.metoffice.weather.android": "weather", "com.weather.Weather": "weather", "com.yahoo.mobile.client.android.weather": "weather", "org.breezyweather": "weather", "de.wetteronline.wetterapp": "weather", "com.samsung.android.weather": "weather",
    "com.google.android.keep": "notes", "com.evernote": "notes", "com.microsoft.office.onenote": "notes", "md.obsidian": "notes", "notion.id": "notes", "com.samsung.android.app.notes": "notes", "com.simplemobiletools.notes.pro": "notes", "com.google.android.apps.docs.editors.docs": "notes", "com.todoist": "notes", "com.ticktick.task": "notes", "com.microsoft.todos": "notes", "com.anydo": "notes", "com.google.android.apps.tasks": "notes",
    "com.amazon.mShop.android.shopping": "shop", "com.ebay.mobile": "shop", "com.etsy.android": "shop", "com.zzkko": "shop", "com.alibaba.aliexpresshd": "shop", "com.einnovation.temu": "shop", "com.argos.android": "shop", "com.tesco.grocery.view": "shop", "com.sainsburys.gol": "shop", "com.asda.android": "shop", "com.morrisons.grocery": "shop", "com.ocado.mobile.android": "shop", "com.vinted.android": "shop", "com.depop": "shop", "com.asos.app": "shop", "com.next.androidapp": "shop", "uk.co.marksandspencer.mands": "shop", "com.johnlewis.app": "shop", "com.currys.app": "shop", "com.ikea.app": "shop", "com.bandq.android": "shop", "com.screwfix.app": "shop", "com.aldi.uk": "shop", "com.lidl.eci.lidlplus": "shop", "com.boots.flagship.android": "shop", "com.superdrug.app": "shop", "com.zara": "shop", "com.hm.goe": "shop", "com.primark.app": "shop", "com.google.android.apps.shopping.express": "shop",
    "com.android.vending": "shop", "com.aurora.store": "shop", "org.fdroid.fdroid": "shop", "com.amazon.venezia": "shop", "com.sec.android.app.samsungapps": "shop",
    # UK banks & money
    "com.monzo.android": "bank", "com.starlingbank.android": "bank", "com.revolut.revolut": "bank", "com.barclays.android.barclaysmobilebanking": "bank", "uk.co.hsbc.hsbcukmobilebanking": "bank", "com.grppl.android.shell.CMBlloydsTSB73": "bank", "com.grppl.android.shell.halifax": "bank", "com.grppl.android.shell.BOS": "bank", "com.rbs.mobile.android.natwest": "bank", "com.rbs.mobile.android.rbs": "bank", "com.nationwide.mobilebanking": "bank", "com.santander.mobile.android": "bank", "uk.co.santander.santanderUK": "bank", "com.tsb.mobilebank": "bank", "com.firstdirect.bankingonthego": "bank", "com.chase.intl": "bank", "co.uk.getmondo": "bank", "com.metrobank.android": "bank", "com.virginmoney.uk.mobile.android": "bank", "com.cooperativebank.bank": "bank", "com.tescobank.mobile": "bank", "com.marcus.android": "bank", "uk.co.monese.app": "bank", "com.wise.android": "bank", "com.transferwise.android": "bank", "com.paypal.android.p2pmobile": "card", "com.google.android.apps.walletnfcrel": "card", "com.google.android.apps.nbu.paisa.user": "card", "com.samsung.android.spay": "card", "com.curve.app": "card", "com.klarna.mobile.app": "card", "com.moneybox.app": "chart", "com.trading212.client": "chart", "com.freetrade.android": "chart", "com.coinbase.android": "chart", "com.binance.dev": "chart", "com.robinhood.android": "chart", "io.metamask": "key", "com.experian.android": "chart", "com.clearscore.android": "chart", "com.moneysupermarket.app": "pound", "com.moneydashboard.app": "pound", "com.emmaapp": "pound", "com.snoop.android": "pound", "com.ynab.app": "pound", "com.hmrc.ptcalc": "pound", "uk.gov.hmrc.ptcalc": "pound", "com.monese": "bank", "com.chip.chipuk": "pound", "com.plum.app": "pound",
    # chat & social
    "com.whatsapp": "bubble", "com.whatsapp.w4b": "bubble", "org.telegram.messenger": "send", "org.thoughtcrime.securesms": "bubble", "com.facebook.orca": "bubble", "com.facebook.katana": "l:F", "com.instagram.android": "camera", "com.twitter.android": "l:X", "com.zhiliaoapp.musically": "music", "com.snapchat.android": "camera", "com.reddit.frontpage": "bubble", "com.discord": "game", "com.Slack": "l:#", "com.microsoft.teams": "l:T", "us.zoom.videomeetings": "video", "com.google.android.apps.meetings": "video", "com.google.android.apps.tachyon": "video", "com.skype.raider": "video", "com.linkedin.android": "l:IN", "com.pinterest": "l:P", "com.tumblr": "l:T", "com.threads": "l:@", "com.bsky.app": "bubble", "org.joinmastodon.android": "bubble", "com.viber.voip": "phone", "jp.naver.line.android": "bubble", "com.tencent.mm": "bubble", "com.kakao.talk": "bubble", "com.nextdoor": "home", "com.facebook.lite": "l:F", "com.instagram.lite": "camera", "com.beeper.android": "bubble", "im.vector.app": "bubble", "com.google.android.apps.messaging.lite": "message",
    # food & transport
    "com.deliveroo.orderapp": "food", "com.ubercab.eats": "food", "com.justeat.app": "food", "uk.co.dominos.android": "food", "com.pizzahut.uk": "food", "com.mcdonalds.mobileapp": "food", "com.starbucks.mobilecard": "food", "com.greggs.greggs": "food", "com.costacoffee.uk": "food", "com.pret.app": "food", "com.toogoodtogo.tgtg": "food", "com.nandos.uk": "food", "com.kfc.mobile": "food", "com.burgerking.uk": "food", "uk.co.subway.subway": "food", "com.gopuff.app": "food", "com.getir.uk": "food", "com.hellofresh.androidapp": "food", "com.gousto.app": "food",
    "com.ubercab": "car", "com.bolt.client": "car", "com.freenow.app": "car", "com.gett.customer": "car", "com.lime.rider": "car", "com.zipcar.android": "car", "com.enterprise.mobile": "car", "com.parkmobile.uk": "car", "com.ringgo.android": "car", "com.justpark.jp": "car", "com.waze.carpool": "car", "com.android.car": "car", "com.google.android.projection.gearhead": "car", "com.tesla.tesla": "car", "com.rac.rac": "car", "uk.co.theaa.app": "car", "com.autotrader.android": "car", "com.petrolprices.app": "car", "com.octopus.octopus": "home", "com.octopusenergy.app": "home",
    "com.thetrainline": "train", "uk.co.nationalrail.google": "train", "com.tfl.tflgo": "train", "uk.gov.tfl.tflgo": "train", "com.lner.app": "train", "com.avanti.westcoast": "train", "com.gwr.app": "train", "com.southeastern.app": "train", "com.northernrail.app": "train", "com.stagecoach.app": "train", "com.firstgroup.app": "train", "com.arriva.arrivauk": "train", "com.nationalexpress.app": "train", "com.megabus.uk": "train", "com.flixbus.app": "train", "uk.co.eurostar.app": "train", "com.ryanair.cheapflights": "send", "com.easyjet.app": "send", "com.ba.mobile": "send", "com.skyscanner.android.main": "send", "com.booking": "home", "com.airbnb.android": "home", "com.tripadvisor.tripadvisor": "map", "com.expedia.bookings": "send", "com.hotels.android": "home", "com.jet2.app": "send", "com.tui.uk": "send", "com.google.android.apps.travel.onthego": "send",
    # parcels
    "uk.co.royalmail.consumer": "parcel", "com.dpd.uk": "parcel", "com.evri.app": "parcel", "com.myhermes.uk": "parcel", "com.ups.mobile.android": "parcel", "com.fedex.ida.android": "parcel", "com.dhl.exp.dhlmobile": "parcel", "com.yodel.app": "parcel", "com.parcelforce.app": "parcel", "com.amazon.delivery": "parcel", "com.inpost.uk": "parcel", "uk.co.dpdgroup.mydpd": "parcel", "com.parcel.track": "parcel", "com.aftership.AfterShip": "parcel", "com.parcelapp": "parcel",
    # health
    "com.google.android.apps.fitness": "fitness", "com.fitbit.FitbitMobile": "fitness", "com.strava": "fitness", "com.garmin.android.apps.connectmobile": "fitness", "com.sec.android.app.shealth": "heart", "com.myfitnesspal.android": "fitness", "com.nike.ntc": "fitness", "com.nike.plusgps": "fitness", "com.runtastic.runtastic": "fitness", "com.peloton.callisto": "fitness", "com.google.android.apps.healthdata": "heart", "com.nhs.online.nhsonline": "heart", "com.nhs.online": "heart", "com.patientaccess.android": "heart", "com.babylon": "heart", "com.headspace.android": "heart", "com.calm.android": "heart", "com.whoop.android": "fitness", "com.ouraring.oura": "heart", "com.huawei.health": "heart", "com.xiaomi.wearable": "fitness", "com.zepp.app": "fitness", "com.withings.wiscale2": "heart", "com.flo.health": "heart", "com.boots.app": "heart", "uk.nhs.covid19.production": "heart", "com.sleepcycle.app": "clock", "com.northcube.sleepcycle": "clock", "com.stepsapp.pedometer": "fitness", "com.pacer.pedometer": "fitness",
    # reading & news
    "com.amazon.kindle": "book", "com.audible.application": "book", "com.google.android.apps.books": "book", "com.kobobooks.android": "book", "org.readera": "book", "com.moon.android.reader": "book", "com.libby": "book", "com.overdrive.mobile.android.libby": "book", "com.scribd.app.reader0": "book", "com.medium.reader": "book", "com.pocket": "book", "bbc.mobile.news.uk": "news", "com.guardian": "news", "com.google.android.apps.magazines": "news", "uk.co.telegraph.android": "news", "com.thetimes.tallyrand": "news", "com.ft.news": "news", "flipboard.app": "news", "com.apple.android.news": "news", "com.reuters.mobile": "news", "com.independent.uk": "news", "com.skynews.app": "news", "com.dailymail.online": "news", "com.economist.lamarr": "news", "com.feedly": "news", "org.wikipedia": "book", "com.duolingo": "book", "com.quizlet.quizletandroid": "book", "com.khanacademy.android": "book", "com.udemy.android": "book", "com.coursera.android": "book", "com.google.android.apps.podcasts": "mic", "fm.castbox.audiobook.radio.podcast": "mic", "au.com.shiftyjelly.pocketcasts": "mic", "com.bbc.sounds": "mic", "uk.co.bbc.android.sounds": "mic", "com.global.globalplayer": "mic", "com.tunein.player": "mic", "com.audiomack": "music",
    # productivity & work
    "com.microsoft.office.word": "l:W", "com.microsoft.office.excel": "l:X", "com.microsoft.office.powerpoint": "l:P", "com.microsoft.office.officehubrow": "l:O", "com.google.android.apps.docs.editors.sheets": "chart", "com.google.android.apps.docs.editors.slides": "l:S", "com.adobe.reader": "book", "com.google.android.apps.translate": "l:A", "com.google.android.googlequicksearchbox": "l:G", "com.google.android.apps.bard": "l:G", "com.openai.chatgpt": "l:AI", "com.anthropic.claude": "l:AI", "com.microsoft.copilot": "l:AI", "com.perplexity.app.android": "l:AI", "ai.x.grok": "l:AI", "com.google.android.apps.authenticator2": "key", "com.azure.authenticator": "key", "com.lastpass.lpandroid": "key", "com.bitwarden.mobile": "key", "com.x8bit.bitwarden": "key", "com.onepassword.android": "key", "com.agilebits.onepassword": "key", "com.dashlane": "key", "com.google.android.apps.chromecast.app": "home", "com.amazon.dee.app": "mic", "com.philips.lighting.hue2": "home", "com.ring.android": "home", "com.hive.android": "home", "com.nest.android": "home", "com.tado": "home", "com.samsung.android.oneconnect": "home", "com.xiaomi.smarthome": "home", "com.tplink.tapo": "home", "com.smartthings.android": "home", "com.google.android.apps.wallpaper": "photo", "com.nothing.thirdparty": "glyph", "com.nothing.glyph": "glyph", "com.nothing.recorder": "mic", "com.nothing.soundrecorder": "mic", "com.google.android.apps.recorder": "mic", "com.android.soundrecorder": "mic", "com.nothing.cardhub": "card", "com.nothing.essentialspace": "notes", "com.nothing.smartcenter": "settings", "com.nothing.hearthstone": "music", "com.nothing.nothingx": "music", "com.nothing.NothingX": "music", "com.nothing.audio": "music", "com.nothing.systemui": "settings", "com.nothing.weather.widget": "weather", "com.nothing.apps.gallery": "gallery", "com.nothing.notes": "notes", "com.nothing.calendar": "calendar", "com.nothing.contacts": "l:C",
    "com.android.vending.billing": "shop", "com.google.android.apps.messaging.web": "message", "com.google.android.apps.walletnfcrel.beta": "card",
    # games & misc
    "com.supercell.clashofclans": "game", "com.supercell.brawlstars": "game", "com.mojang.minecraftpe": "game", "com.roblox.client": "game", "com.king.candycrushsaga": "game", "com.nianticlabs.pokemongo": "game", "com.epicgames.fortnite": "game", "com.activision.callofduty.shooter": "game", "com.tencent.ig": "game", "com.miHoYo.GenshinImpact": "game", "com.ea.gp.fifamobile": "game", "com.innersloth.spacemafia": "game", "com.valvesoftware.android.steam.community": "game", "com.xbox.app": "game", "com.microsoft.xboxone.smartglass": "game", "com.playstation.mobilemessenger": "game", "com.scee.psxandroid": "game", "com.nintendo.znca": "game", "com.nytimes.crossword": "game", "com.nytimes.android": "news", "com.wordle": "game", "com.chess": "game", "com.geopolis.wordle": "game",
    "com.google.android.apps.subscriptions.red": "l:1", "com.google.android.apps.youtube.kids": "play", "com.google.android.apps.classroom": "book", "com.google.android.apps.nbu.paisa": "card", "com.google.android.apps.messaging": "message", "com.google.android.apps.adm": "map", "com.google.android.apps.pixel.support": "settings", "com.google.android.apps.safetyhub": "lock", "com.google.android.apps.wellbeing": "clock", "com.google.android.apps.dynamite": "bubble", "com.google.android.apps.chrome": "browser", "com.google.android.apps.youtube.creator": "play", "com.google.ar.lens": "camera", "com.google.android.apps.lens": "camera", "com.google.android.apps.turbo": "settings", "com.google.android.apps.nexuslauncher": "home",
    "uk.nothingsuite.dotwidgets": "dots", "uk.nothingsuite.glyphtracker": "glyph", "uk.nothingsuite.dotsicons": "dots", "uk.nothingsuite.nowplaying": "music", "uk.nothingsuite.app": "phone", "uk.nothingsuite.shadeguard": "lock",
    "com.aisense.otter": "mic", "com.bskyb.skynews.android": "news", "com.connect.enduser": "camera", "com.lojack.bmwsecurity": "car", "de.bmw.connected.mobile20.row": "car", "uk.co.hsbc.hsbcukbusinessbanking": "bank", "com.nothing.ntessentialspace": "notes", "com.google.android.apps.subscriptions.red": "cloud", "com.google.android.apps.adm": "map", "com.google.android.apps.safetyhub": "lock",
}


# ------------------------------------------------------- components ----
# package → launcher activity. Launchers match icons on the EXACT pair, so the
# more of these we have the better. Add from "EXPORT APP LIST" output.
COMPONENTS = {
    "com.aisense.otter": "com.aisense.otter.ui.feature.main.MainActivity",
    "com.amazon.mShop.android.shopping": "com.amazon.mShop.home.HomeActivity",
    "com.android.chrome": "com.google.android.apps.chrome.Main",
    "com.android.settings": "com.android.settings.Settings",
    "com.android.vending": "com.android.vending.AssetBrowserActivity",
    "com.anthropic.claude": "com.anthropic.claude.mainactivity.MainActivity",
    "com.bskyb.skynews.android": "com.sky.sport.group.MainActivity",
    "com.connect.enduser": "com.hikvision.hikconnect.login.LoadingActivity",
    "com.fitbit.FitbitMobile": "com.fitbit.HealthBrandedAlias",
    "com.google.android.apps.adm": "com.google.android.apps.adm.activities.MainActivity",
    "com.google.android.apps.bard": "com.google.android.apps.bard.shellapp.BardEntryPointActivity",
    "com.google.android.apps.chromecast.app": "com.google.android.apps.chromecast.app.DiscoveryActivity",
    "com.google.android.apps.docs": "com.google.android.apps.docs.app.NewMainProxyActivity",
    "com.google.android.apps.maps": "com.google.android.maps.MapsActivity",
    "com.google.android.apps.messaging": "com.google.android.apps.messaging.ui.ConversationListActivity",
    "com.google.android.apps.nbu.files": "com.google.android.apps.nbu.files.home.HomeActivity",
    "com.google.android.apps.photos": "com.google.android.apps.photos.home.HomeActivity",
    "com.google.android.apps.safetyhub": "com.google.android.apps.safetyhub.LauncherActivity",
    "com.google.android.apps.subscriptions.red": "com.google.android.apps.subscriptions.red.LauncherActivity",
    "com.google.android.apps.tachyon": "com.google.android.apps.tachyon.MainActivity",
    "com.google.android.apps.walletnfcrel": "com.google.commerce.tapandpay.android.wallet.WalletActivity",
    "com.google.android.calculator": "com.android.calculator2.Calculator",
    "com.google.android.calendar": "com.android.calendar.AllInOneActivity",
    "com.google.android.contacts": "com.android.contacts.activities.PeopleActivity",
    "com.google.android.deskclock": "com.android.deskclock.DeskClock",
    "com.google.android.dialer": "com.google.android.dialer.extensions.GoogleDialtactsActivity",
    "com.google.android.gm": "com.google.android.gm.ConversationListActivityGmail",
    "com.google.android.googlequicksearchbox": "com.google.android.googlequicksearchbox.SearchActivity",
    "com.google.android.keep": "com.google.android.keep.activities.BrowseActivity",
    "com.google.android.youtube": "com.google.android.youtube.app.honeycomb.Shell$HomeActivity",
    "com.lojack.bmwsecurity": "com.lojack.bmwsecurity.MainActivity",
    "com.nothing.camera": "com.nothing.camera.activity.CameraActivity",
    "com.nothing.gallery": "com.nothing.gallery.activity.EntryActivity",
    "com.nothing.ntessentialspace": "com.nothing.ntessentialspace.MainActivity",
    "com.nothing.smartcenter": "com.nothing.smartcenter.host.MainActivity",
    "com.nothing.soundrecorder": "com.nothing.soundrecorder.MainActivity",
    "com.nothing.weather": "com.nothing.weather.ui.main.MainActivity",
    "com.openai.chatgpt": "com.openai.chatgpt.MainActivity",
    "com.spotify.music": "com.spotify.music.MainActivity",
    "com.viber.voip": "com.viber.voip.WelcomeActivity",
    "com.whatsapp": "com.whatsapp.Main",
    "com.zhiliaoapp.musically": "com.ss.android.ugc.aweme.splash.SplashActivity",
    "de.bmw.connected.mobile20.row": "com.mobileconnected.MainActivity",
    "uk.co.hsbc.hsbcukbusinessbanking": "com.hsbc.mobilebanking.splash.SplashActivity",
    "uk.nothingsuite.dotsicons": "uk.nothingsuite.dotsicons.MainActivity",
    "uk.nothingsuite.dotwidgets": "uk.nothingsuite.dotwidgets.MainActivity",
    "uk.nothingsuite.glyphtracker": "uk.nothingsuite.glyphtracker.MainActivity",
    "uk.nothingsuite.nowplaying": "uk.nothingsuite.nowplaying.MainActivity",
    "uk.nothingsuite.shadeguard": "uk.nothingsuite.shadeguard.MainActivity",
    # well-known ones from other phones
    "com.instagram.android": "com.instagram.mainactivity.MainActivity",
    "com.facebook.katana": "com.facebook.katana.LoginActivity",
    "com.facebook.orca": "com.facebook.orca.auth.StartScreenActivity",
    "com.twitter.android": "com.twitter.android.StartActivity",
    "com.netflix.mediaclient": "com.netflix.mediaclient.ui.launch.UIWebViewActivity",
    "org.telegram.messenger": "org.telegram.ui.LaunchActivity",
    "com.reddit.frontpage": "launcher.default",
    "com.discord": "com.discord.main.MainDefault",
    "com.snapchat.android": "com.snap.mushroom.MainActivity",
    "com.microsoft.office.outlook": "com.microsoft.office.outlook.MainActivity",
    "com.microsoft.teams": "com.microsoft.skype.teams.Launcher",
    "org.mozilla.firefox": "org.mozilla.fenix.App",
    "com.brave.browser": "com.google.android.apps.chrome.Main",
    "com.microsoft.emmx": "com.microsoft.ruby.Main",
    "com.ubercab": "com.ubercab.presidio.app.core.root.RootActivity",
    "com.ubercab.eats": "com.ubercab.eats.app.EatsActivity",
    "com.deliveroo.orderapp": "com.deliveroo.orderapp.home.ui.HomeActivity",
    "com.justeat.app": "com.justeat.app.ui.launch.LaunchActivity",
    "com.thetrainline": "com.thetrainline.home.HomeActivity",
    "uk.co.royalmail.consumer": "uk.co.royalmail.consumer.MainActivity",
    "com.monzo.android": "co.uk.getmondo.main.MainActivity",
    "com.starlingbank.android": "com.starlingbank.android.MainActivity",
    "com.revolut.revolut": "com.revolut.ui.MainActivity",
    "com.paypal.android.p2pmobile": "com.paypal.android.p2pmobile.startup.activities.StartupActivity",
    "com.google.android.apps.youtube.music": "com.google.android.apps.youtube.music.activities.MusicActivity",
    "com.amazon.kindle": "com.amazon.kindle.UpgradePage",
    "com.strava": "com.strava.SplashActivity",
    "com.duolingo": "com.duolingo.app.LoginActivity",
    "com.ebay.mobile": "com.ebay.mobile.activities.MainActivity",
    "com.samsung.android.dialer": "com.samsung.android.dialer.DialtactsActivity",
    "com.sec.android.app.camera": "com.sec.android.app.camera.Camera",
    "com.nothing.launcher": "com.nothing.launcher.NothingLauncher",
}

# ---------------------------------------------------------------- draw ----
def rounded_tile():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, SIZE - 1, SIZE - 1), radius=int(SIZE * 0.22), fill=BLACK)
    return img

def draw_grid(img, rows, cols_total, color=WHITE):
    """Centre a grid of '#' cells on the tile; each cell becomes a round dot."""
    d = ImageDraw.Draw(img)
    cell = SIZE / cols_total
    h = len(rows); w = max(len(r) for r in rows)
    ox = (SIZE - w * cell) / 2; oy = (SIZE - h * cell) / 2
    r = cell * 0.29
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "#":
                cx = ox + (x + 0.5) * cell; cy = oy + (y + 0.5) * cell
                d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color)

def pictogram_png(rows):
    img = rounded_tile(); draw_grid(img, rows, DOT_GRID); return img

def letter_png(text, font):
    """One or two characters from the 5×7 font, at a scale that fits."""
    text = text.upper()
    glyphs = [font.get(c, font["."]) for c in text]
    rows = ["".join(g[i] + ("." if k < len(glyphs) - 1 else "") for k, g in enumerate(glyphs)) for i in range(7)]
    cols = 9 if len(text) == 1 else 15   # 5 wide + margins; 11 wide + margins
    img = rounded_tile(); draw_grid(img, rows, cols); return img

def safe(name): return re.sub(r"[^a-z0-9_]", "_", name.lower())

def main():
    font = load_font()
    out = os.path.join(RES, "drawable-nodpi"); os.makedirs(out, exist_ok=True)
    os.makedirs(os.path.join(RES, "xml"), exist_ok=True); os.makedirs(os.path.join(RES, "values"), exist_ok=True)
    os.makedirs(os.path.join(HERE, "src/main/assets"), exist_ok=True)
    for f in os.listdir(out):
        if f.startswith(("p_", "l_")) or f in ("iconback.png",): os.remove(os.path.join(out, f))

    # pictograms
    for name, rows in P.items(): pictogram_png(rows).save(os.path.join(out, f"p_{name}.png"))
    # letters/digits + the few two-letter ones the map uses
    letters = set("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#@") | {v[2:] for v in APPS.values() if v.startswith("l:")}
    letter_files = {}
    for t in sorted(letters):
        fn = "l_" + safe(t.replace("#", "hash").replace("@", "at"))
        letter_png(t, font).save(os.path.join(out, fn + ".png")); letter_files[t] = fn
    # plain tile for unknown apps (launcher draws the original icon on top, scaled)
    rounded_tile().save(os.path.join(out, "iconback.png"))
    # launcher icon for the pack itself
    pictogram_png(P["dots"]).save(os.path.join(out, "ic_launcher.png"))

    # appfilter: component names. Activity unknown → wildcard on the package, which the
    # common launchers accept: ComponentInfo{package/} ... we list package-level entries.
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>",
             '    <iconback img1="iconback" />', '    <iconupon />', '    <scale factor="0.62" />']
    used = set()
    for pkg, v in APPS.items():
        drawable = ("p_" + v) if not v.startswith("l:") else letter_files[v[2:].upper()]
        used.add(drawable)
        act = COMPONENTS.get(pkg)
        if act: lines.append(f'    <item component="ComponentInfo{{{pkg}/{act}}}" drawable="{drawable}" />')
        lines.append(f'    <item component="ComponentInfo{{{pkg}/}}" drawable="{drawable}" />')
    lines.append("</resources>")
    appfilter = "\n".join(lines) + "\n"
    open(os.path.join(RES, "xml/appfilter.xml"), "w").write(appfilter)
    open(os.path.join(HERE, "src/main/assets/appfilter.xml"), "w").write(appfilter)

    # drawable.xml: what the launcher's "pick an icon" dialog lists
    alld = sorted({f"p_{n}" for n in P} | set(letter_files.values()))
    dl = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", '    <version>1</version>', '    <category title="Pictograms" />']
    dl += [f'    <item drawable="p_{n}" />' for n in P]
    dl += ['    <category title="Letters" />'] + [f'    <item drawable="{letter_files[t]}" />' for t in sorted(letters)]
    dl.append("</resources>")
    open(os.path.join(RES, "xml/drawable.xml"), "w").write("\n".join(dl) + "\n")
    open(os.path.join(HERE, "src/main/assets/drawable.xml"), "w").write("\n".join(dl) + "\n")

    # iconpack.xml: string-array some launchers read instead
    ip = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>", '    <string-array name="icon_pack">']
    ip += [f"        <item>{d}</item>" for d in alld] + ["    </string-array>", "</resources>"]
    open(os.path.join(RES, "values/iconpack.xml"), "w").write("\n".join(ip) + "\n")

    print(f"{len(P)} pictograms, {len(letters)} letter icons, {len(APPS)} apps mapped, {len(COMPONENTS)} exact")

if __name__ == "__main__":
    main()
