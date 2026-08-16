package com.focuslock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Rotating faceless botanical line art drawn locally with no image assets. */
public class NatureLineArtView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int variant;

    public NatureLineArtView(Context context, int variant) {
        super(context);
        this.variant = Math.floorMod(variant, 8);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2.2f));
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(Color.rgb(52, 116, 76));
        soft.setStyle(Paint.Style.FILL);
        soft.setColor(Color.rgb(240, 248, 239));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        c.drawCircle(w / 2, h / 2, Math.min(w, h) * .42f, soft);
        switch (variant) {
            case 0: sprout(c, w, h); break;
            case 1: mountains(c, w, h); break;
            case 2: waves(c, w, h); break;
            case 3: branch(c, w, h); break;
            case 4: sunrise(c, w, h); break;
            case 5: quietPath(c, w, h); break;
            case 6: fallingLeaves(c, w, h); break;
            default: garden(c, w, h); break;
        }
    }

    private void sprout(Canvas c, float w, float h) {
        c.drawLine(w*.5f,h*.72f,w*.5f,h*.35f,line);
        leaf(c,w*.5f,h*.46f,w*.28f,h*.31f);
        leaf(c,w*.5f,h*.57f,w*.72f,h*.41f);
        arc(c,w*.25f,h*.74f,w*.75f,h*.86f,190,160);
    }
    private void mountains(Canvas c, float w, float h) {
        Path p=new Path(); p.moveTo(w*.15f,h*.72f); p.lineTo(w*.38f,h*.39f); p.lineTo(w*.53f,h*.61f); p.lineTo(w*.67f,h*.31f); p.lineTo(w*.86f,h*.72f); c.drawPath(p,line);
        c.drawCircle(w*.72f,h*.24f,w*.07f,line); arc(c,w*.13f,h*.67f,w*.88f,h*.84f,185,170);
    }
    private void waves(Canvas c, float w, float h) {
        for(int i=0;i<4;i++){ Path p=new Path(); float y=h*(.38f+i*.11f); p.moveTo(w*.16f,y); p.cubicTo(w*.31f,y-h*.08f,w*.43f,y+h*.08f,w*.56f,y); p.cubicTo(w*.69f,y-h*.08f,w*.78f,y+h*.04f,w*.86f,y); c.drawPath(p,line); }
        c.drawCircle(w*.28f,h*.25f,w*.06f,line);
    }
    private void branch(Canvas c, float w, float h) {
        Path p=new Path(); p.moveTo(w*.2f,h*.78f); p.cubicTo(w*.38f,h*.59f,w*.47f,h*.42f,w*.79f,h*.24f); c.drawPath(p,line);
        leaf(c,w*.38f,h*.61f,w*.2f,h*.48f); leaf(c,w*.47f,h*.49f,w*.65f,h*.38f); leaf(c,w*.62f,h*.35f,w*.49f,h*.21f); leaf(c,w*.72f,h*.28f,w*.86f,h*.38f);
    }
    private void sunrise(Canvas c, float w, float h) {
        arc(c,w*.28f,h*.38f,w*.72f,h*.76f,180,180); c.drawLine(w*.15f,h*.76f,w*.85f,h*.76f,line);
        for(int i=0;i<5;i++){ double a=Math.PI+(i*Math.PI/4); float x1=w*.5f+(float)Math.cos(a)*w*.25f, y1=h*.72f+(float)Math.sin(a)*w*.25f; float x2=w*.5f+(float)Math.cos(a)*w*.34f, y2=h*.72f+(float)Math.sin(a)*w*.34f; c.drawLine(x1,y1,x2,y2,line); }
    }
    private void quietPath(Canvas c, float w, float h) {
        Path l=new Path(); l.moveTo(w*.22f,h*.82f); l.cubicTo(w*.37f,h*.61f,w*.42f,h*.52f,w*.48f,h*.25f); c.drawPath(l,line);
        Path r=new Path(); r.moveTo(w*.78f,h*.82f); r.cubicTo(w*.59f,h*.63f,w*.55f,h*.48f,w*.52f,h*.25f); c.drawPath(r,line);
        leaf(c,w*.28f,h*.53f,w*.14f,h*.39f); leaf(c,w*.7f,h*.44f,w*.84f,h*.3f);
    }
    private void fallingLeaves(Canvas c, float w, float h) {
        leaf(c,w*.23f,h*.34f,w*.41f,h*.25f); leaf(c,w*.58f,h*.29f,w*.76f,h*.39f); leaf(c,w*.31f,h*.66f,w*.5f,h*.54f); leaf(c,w*.62f,h*.7f,w*.8f,h*.58f);
        Path p=new Path(); p.moveTo(w*.17f,h*.2f); p.cubicTo(w*.44f,h*.4f,w*.5f,h*.55f,w*.83f,h*.82f); c.drawPath(p,line);
    }
    private void garden(Canvas c, float w, float h) {
        c.drawLine(w*.2f,h*.76f,w*.8f,h*.76f,line);
        for(int i=0;i<3;i++){ float x=w*(.32f+i*.18f), top=h*(.34f+(i%2)*.08f); c.drawLine(x,h*.76f,x,top,line); leaf(c,x,h*.54f,x-w*.12f,h*.44f); leaf(c,x,h*.64f,x+w*.12f,h*.53f); c.drawCircle(x,top,w*.035f,line); }
    }

    private void leaf(Canvas c,float x1,float y1,float x2,float y2){ Path p=new Path(); p.moveTo(x1,y1); float mx=(x1+x2)/2,my=(y1+y2)/2; p.quadTo(mx+(y1-y2)*.28f,my+(x2-x1)*.28f,x2,y2); p.quadTo(mx-(y1-y2)*.28f,my-(x2-x1)*.28f,x1,y1); c.drawPath(p,line); c.drawLine(x1,y1,x2,y2,line); }
    private void arc(Canvas c,float l,float t,float r,float b,float start,float sweep){ c.drawArc(l,t,r,b,start,sweep,false,line); }
    private float dp(float v){ return v*getResources().getDisplayMetrics().density; }
}
