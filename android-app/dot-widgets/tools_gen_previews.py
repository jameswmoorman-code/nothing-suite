import os,sys
from PIL import Image, ImageDraw, ImageFont
ROOT=sys.argv[1]
B=ROOT+'/core-design/src/main/res/font/doto_round.ttf'; L=ROOT+'/core-design/src/main/res/font/doto_round_light.ttf'
OUT=ROOT+'/dot-widgets/src/main/res/drawable-nodpi'; os.makedirs(OUT,exist_ok=True)
W,H=520,240; R=32
WHITE=(255,255,255); GREY=(122,122,122); RED=(215,25,33)
def card():
    img=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(img)
    d.rounded_rectangle((0,0,W-1,H-1),R,fill=(0,0,0,255),outline=(58,58,58,255),width=2); return img,d
def spaced(d,xy,text,font,fill,spacing):
    widths=[d.textlength(ch,font=font) for ch in text]; sp=spacing*font.size
    total=sum(widths)+sp*(len(text)-1); x=xy[0]-total/2
    for ch,w in zip(text,widths):
        d.text((x+w/2,xy[1]),ch,font=font,fill=fill,anchor='mm'); x+=w+sp
def hero(d,y,t,fill=WHITE): spaced(d,(W/2,y),t,ImageFont.truetype(B,72),fill,0.12)
def title(d,y,t,fill=WHITE): spaced(d,(W/2,y),t,ImageFont.truetype(B,40),fill,0.12)
def label(d,y,t,fill=GREY): spaced(d,(W/2,y),t,ImageFont.truetype(L,18),fill,0.2)
def dots(d,y,frac,n=10,fill=WHITE):
    r=5;gap=8;total=n*2*r+(n-1)*gap;x=W/2-total/2
    for i in range(n):
        box=(x+i*(2*r+gap),y-r,x+i*(2*r+gap)+2*r,y+r)
        if i<int(frac*n): d.ellipse(box,fill=fill)
        else: d.ellipse(box,outline=fill,width=2)
def icon(d,cy,grid,fill=WHITE):
    rows=grid.strip('\n').split('\n'); s=6
    for y,row in enumerate(rows):
        for x,c in enumerate(row):
            if c=='#':
                cx=W/2+(x-5)*s; yy=cy+(y-5)*s; d.ellipse((cx-2,yy-2,cx+2,yy+2),fill=fill)
CIG="""
.........#.
........#..
.........#.
...........
####.......
###########
###########
####.......
...........
...........
..........."""
SUN="""
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
.....#....."""
out={}
img,d=card(); hero(d,100,"10:24"); label(d,165,"SUN 20 SEPT"); out['clock']=img
img,d=card(); label(d,85,"SUNDAY"); title(d,135,"20 SEPTEMBER"); out['date']=img
img,d=card(); hero(d,95,"82%"); label(d,150,"CHARGING",RED); dots(d,185,0.82); out['battery']=img
img,d=card(); label(d,60,"DAY 62%"); dots(d,85,0.62,20); label(d,115,"MONTH 50%"); dots(d,140,0.5,20); label(d,170,"YEAR 71%"); dots(d,195,0.71,20,RED); out['progress']=img
img,d=card(); label(d,70,"WEEKEND"); hero(d,120,"3"); label(d,175,"DAYS",RED); out['countdown']=img
img,d=card(); icon(d,55,CIG); label(d,100,"SMOKE FREE"); hero(d,150,"47"); label(d,200,"DAYS",RED); out['streak']=img
img,d=card(); title(d,120,"STAY CURIOUS"); out['label']=img
img,d=card(); label(d,60,"STEPS"); hero(d,110,"6,240"); label(d,160,"OF 8,000"); dots(d,195,0.78); out['steps']=img
img,d=card(); label(d,70,"MON 14:30"); title(d,120,"DENTIST"); label(d,170,"IN 2 HRS",RED); out['nextup']=img
img,d=card(); icon(d,50,SUN); hero(d,120,"16°"); label(d,170,"CLEAR"); label(d,200,"LONDON",WHITE); out['weather']=img
img,d=card(); spaced(d,(W/2,110),"LESS, BUT BETTER",ImageFont.truetype(B,30),WHITE,0.12); label(d,160,"DIETER RAMS"); out['quote']=img
img,d=card(); hero(d,100,"64 GB"); label(d,150,"FREE OF 256"); dots(d,185,0.75); out['storage']=img
for n,img in out.items(): img.save(f'{OUT}/preview_{n}.png')
print(len(out),'previews')
