package com.teratail.q_qnlefk433nklk8;

import android.content.res.Resources;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;

import java.util.*;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {

  private class Life {
    private List<ImageView> viewList = new ArrayList<>();
    private int count;

    Life(@IdRes int... ids) {
      count = ids.length;
      for(@IdRes int id : ids) viewList.add(findViewById(id));
    }

    boolean decrement() {
      if(count > 0) viewList.get(--count).setImageResource(R.drawable.hpdown);
      return count == 0;
    }
  }

  private static class ImageRepository {
    private Resources resources;
    private Map<Integer,Bitmap> imageMap = new HashMap<>();

    ImageRepository(Resources resources) {
      this.resources = resources;
    }
    ImageRepository prepare(@DrawableRes int... ids) {
      for(@DrawableRes int id : ids) imageMap.put(id, BitmapFactory.decodeResource(resources, id));
      return this;
    }

    Bitmap get(@DrawableRes int id) {
      if(!imageMap.containsKey(id)) prepare(id);
      return imageMap.get(id);
    }
  }

  private abstract class Character {
    protected Bitmap image;
    protected int x, y, w, h;

    Character(@DrawableRes int id) {
      image = imageRepository.get(id);
      x = 0;
      y = 0;
      w = image.getWidth();
      h = image.getHeight();
    }

    Rect getRect() {
      return new Rect(x, y, x+w, y+h);
    }
    void draw(Canvas canvas, Paint paint) {
      canvas.drawBitmap(image, x, y, paint);
    }
  }

  private class Bullet extends Character {
    private int dy;
    private Consumer<Bullet> outsizeListener;

    Bullet(@DrawableRes int id, int cx, int y, int dy, Consumer<Bullet> outsizeListener) {
      super(id);
      this.x = cx - w / 2;
      this.y = y - (dy<0?h:0);
      this.dy = dy;
      this.outsizeListener = outsizeListener;
    }

    private void changePos() {
      y += dy;
      if(y+h < 0 || frameHeight < y) if(outsizeListener != null) outsizeListener.accept(this);
    }
  }

  private class Box extends Character {
    private static final int FIRING_INTERVAL = 75;

    private @DrawableRes int bulletId;
    private List<Bullet> bulletList = new ArrayList<>();
    private Consumer<Bullet> outsizeListener = b -> bulletList.remove(b);
    private int firingIntervalCount = 0;

    Box(int cx, int y, @DrawableRes int id, @DrawableRes int bulletId) {
      super(id);
      this.x = cx - w / 2;
      this.y = y;
      this.bulletId = bulletId;
    }

    private void changePos() {
      if(action_point != null) {
        x = action_point.x - w / 2;
        y = action_point.y - h / 2;
      }

      for(int i=bulletList.size()-1; i>=0; i--) bulletList.get(i).changePos();

      if(firingIntervalCount <= 0) {
        if(action_point != null) {
          firingIntervalCount = FIRING_INTERVAL;
          fire();
        }
      } else {
        firingIntervalCount --;
      }
    }

    @Override
    void draw(Canvas canvas, Paint paint) {
      super.draw(canvas, paint);
      for(Bullet b : bulletList) b.draw(canvas, paint);
    }

    void fire() {
      bulletList.add(new Bullet(bulletId, x+w/2, y, -12, outsizeListener));
    }

    boolean bulletsHitTo(Character target) {
      Rect rect = target.getRect();
      for(Bullet bullet : bulletList) {
        if(Rect.intersects(bullet.getRect(), rect)) {
          bulletList.remove(bullet);
          return true;
        }
      }
      return false;
    }
  }

  private class Boss extends Character {
    private static final int FIRING_INTERVAL = 50;

    private @DrawableRes int bulletId;
    private int dx;
    private List<Bullet> bulletList = new ArrayList<>();
    private Consumer<Bullet> outsizeListener = b -> bulletList.remove(b);
    private int firingIntervalCount = 0;

    Boss(@DrawableRes int id, @DrawableRes int bulletId) {
      super(id);
      this.bulletId = bulletId;
      dx = 12;
    }

    private void changePos() {
      x += dx;
      if(x < 0 || frameWidth <= x+w) dx = -dx;

      for(int i=bulletList.size()-1; i>=0; i--) bulletList.get(i).changePos();

      if(--firingIntervalCount <= 0) {
        firingIntervalCount = FIRING_INTERVAL;
        fire();
      }
    }

    @Override
    void draw(Canvas canvas, Paint paint) {
      super.draw(canvas, paint);
      for(Bullet b : bulletList) b.draw(canvas, paint);
    }

    void fire() {
      bulletList.add(new Bullet(bulletId, x+w/2, y+h, 12, outsizeListener));
    }

    boolean bulletsHitTo(Character target) {
      Rect rect = target.getRect();
      for(Bullet bullet : bulletList) {
        if(Rect.intersects(bullet.getRect(), rect)) {
          bulletList.remove(bullet);
          return true;
        }
      }
      return false;
    }
  }

  private TextView scoreLabel;
  private TextView startLabel;
  private SurfaceView surfaceView;

  private int frameHeight;
  private int frameWidth;
  private int score = 0;

  private ImageRepository imageRepository;
  private Box box;
  private Boss boss;
  private Life life;

  private Timer timer = new Timer();

  private Point action_point = null; // 画面をタップしたらタップ座標、画面から指を離したらnull
  private boolean start_flg = false; //ゲーム開始前はfalse、ゲームプレイ中はtrue

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    imageRepository = new ImageRepository(getResources())
            .prepare(R.drawable.mytama, R.drawable.orange);

    scoreLabel = findViewById(R.id.scoreLabel);
    scoreLabel.setText("Score : 0");

    startLabel = findViewById(R.id.startLabel);

    surfaceView = findViewById(R.id.frame);
    surfaceView.setVisibility(View.INVISIBLE);
    surfaceView.setOnTouchListener((v, event) -> {
      if(event.getAction() == MotionEvent.ACTION_DOWN) { //タップしていたら
        action_point = new Point((int)event.getX(), (int)event.getY());
      } else if(event.getAction() == MotionEvent.ACTION_UP) { //指を話していたら
        action_point = null;
      }
      return true;
    });
  }

  private void changePos(MotionEvent event) {
    // 自分の機体・弾
    box.changePos();

    //敵の機体・弾
    boss.changePos();

    hitCheck();

    SurfaceHolder holder = surfaceView.getHolder();
    Canvas canvas = holder.lockCanvas();

    canvas.drawColor(Color.BLACK);

    box.draw(canvas, new Paint());

    boss.draw(canvas, new Paint());

    holder.unlockCanvasAndPost(canvas);

    scoreLabel.setText("Score : " + score);
  }

  private void hitCheck() {
    //自分の弾がボスに当たった?
    if(box.bulletsHitTo(boss)) {
      score += 10;
    }

    // ボスの弾が自分に当たった?
    if(boss.bulletsHitTo(box)) {
      if(life.decrement()) {
        // Game Over!
        if(timer != null) {
          timer.cancel();
          timer = null;
        }

        // 結果画面へ
        //Intent intent = new Intent(getApplicationContext(), ResultActivity.class);
        //intent.putExtra("SCORE", score);
        //startActivity(intent);
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if(start_flg) return false;

    start_flg = true;

    frameHeight = surfaceView.getHeight();
    frameWidth = surfaceView.getWidth();

    box = new Box(frameWidth/2, (int)(frameHeight*0.8), R.drawable.box, R.drawable.mytama);
    boss = new Boss(R.drawable.boss, R.drawable.orange);
    life = new Life(R.id.life1, R.id.life2, R.id.life3);

    startLabel.setVisibility(View.GONE);
    surfaceView.setVisibility(View.VISIBLE);

    timer.schedule(new TimerTask() {
      @Override
      public void run() {
        changePos(event);
      }
    }, 0, 20);

    return true;
  }
}