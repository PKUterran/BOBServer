package com.huisedenanhai;

import java.awt.*;
import java.util.*;

abstract public class Model
{
    public static class Ball
    {
        public static final double MAX_MAX_VELOCITY = 20.;                  //速度上限的上限：球越大，速度上限越小
        public static final double ACCELERATION_VARY = 0.4;                 //施力时的加速度大小

        public double x,y,vx,vy,ax,ay;
        public double size;                                                 //球大小，用于计算半径
        public double getNegativeSize() { return -size; }
        public final double getRadium(){ return Math.sqrt(size / Math.PI); }//获取半径
        public int ID;
        public final Color color;
        public static final Color[] COLORS = {Color.WHITE, Color.PINK, Color.CYAN, Color.YELLOW, Color.GREEN};
        public static final int COLOR_NUM = COLORS.length;
        //营养，边长，标号，位置，颜色

        public Ball(int ID,
                    double x, double y,
                    double vx, double vy,
                    double ax, double ay,
                    Color color, double size)
        {
            this.ID = ID;
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.ax = ax; this.ay = ay;
            this.color = color;
            this.size = size;
        }

        /**
         * 获取球的最大速度
         * 根据球自身大小，速度上限从MAX_MAX_VELOCITY逐渐降低至MAX_MAX_VELOCITY的一半
         * @return max velocity of my size
         */
        double getMaxVelocity()
        {
            double temp = size / (size + 300.);
            return MAX_MAX_VELOCITY * (1 - temp / 2);
        }

        /**
         * 速度变化
         * 考虑速度上限
         * 超过最大值就将x, y等比例缩减
         * @param deltaV    {deltaVX, deltaVY}
         */
        void varyVelocity(double[] deltaV)
        {
            double maxV = getMaxVelocity();
            vx += deltaV[0];
            vy += deltaV[1];
            	
            vx = Math.min(vx, maxV);
            vy = Math.min(vy, maxV);
            vx = Math.max(vx, -maxV);
            vy = Math.max(vy, -maxV);
            
            if (vx * vx + vy * vy > MAX_MAX_VELOCITY) {
            	double div = vx / vy;
            	vx = MAX_MAX_VELOCITY / Math.sqrt(div * div + 1);
            	vy = div * vx;
            }
        }

        /**
         * 位置变化
         * 当球中心碰壁时，令其弹回
         * @param deltaP
         */
        void varyPosition(double[] deltaP)
        {
            x += deltaP[0];
            y += deltaP[1];
            if (x > WIDTH){
                x = WIDTH - (x - WIDTH);
                vx = -vx;
            }
            if (x < 0) {
                x = -x;
                vx = -vx;
            }
            if (y > HEIGHT) {
                y = HEIGHT - (y - HEIGHT);
                vy = -vy;
            }
            if (y < 0) {
                y = -y;
                vy = -vy;
            }
        }

        /**
         * 判断能否吃零食
         * @param s the snack to eat
         * @return  if this ball can eat this snack?
         */
        boolean isCloseEnough(Snack s)
        {
            double distance = Math.sqrt((x-s.x)*(x-s.x)+(y-s.y)*(y-s.y));
            return distance <= getRadium();
        }

        /**
         * 判断能否吃其他球
         * @param b the smaller ball to eat
         * @return  if this ball can eat that one?
         */
        boolean isCloseEnough(Ball b)
        {
            double distance = Math.sqrt((x-b.x)*(x-b.x)+(y-b.y)*(y-b.y));
            return distance <= getRadium();
        }
    }

    public static class Snack
    {
        public static final Color[] COLORS = {Color.WHITE, Color.PINK, Color.CYAN, Color.YELLOW, Color.GREEN};
        public static final int COLOR_NUM = COLORS.length;
        //营养，边长，标号，位置，颜色
        public static final double NUTRITION = 4.;
        public static final double SIDE = 2.;
        public int index;
        double x,y;
        Color color;
        long time;                      //生成时间
        boolean wasEaten;               //是否已经被服务器告知被吃

        Snack(int x, int y, Color color, long time, int index)
        {
            this.x = x;
            this.y = y;
            this.time = time;
            this.color = color;
            this.index = index;
        }
    }

    int ID;                                               //客户端ID

    public static final int WIDTH = 5000;                   //游戏区域
    public static final int HEIGHT = 4000;
    public static final long FRAME_SIZE = 20;               //每帧的时长（ms）
    public static final long SEED = 1000000007L;            //种子，保证每个模型的零食生成序列相同
    public static final int MAX_SNACK_SIZE = 50000;         //在这场游戏中，总共会生成多少零食
    public static final double SNACK_PER_MS = 1.;           //每ms生成的零食个数
    public long currentTime = 0;
    Random snackRandom = new Random(SEED);
    Snack[] globalSnacks = new Snack[MAX_SNACK_SIZE];       //零食及其状态

    public Model(long time)
    {
    	currentTime = time;
        for (int i = 0; i < MAX_SNACK_SIZE; i++)
            globalSnacks[i] = new Snack(
                    snackRandom.nextInt(WIDTH),
                    snackRandom.nextInt(HEIGHT),
                    Snack.COLORS[snackRandom.nextInt(Snack.COLOR_NUM)],
                    (long) (i / SNACK_PER_MS),
                    i
            );
    }
}
