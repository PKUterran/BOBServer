package com.huisedenanhai;

import java.util.*;

public class ServerModel extends Model
{
    int clientNum;
    List<ClientAcceleration> clientAccs = new ArrayList<>();
    public static final long BALLSEED = 1000000003L;
    Status status = new Status();	//上一帧的状态
    
    /**
     * 新加入Client
     * @param ID
     * @param time
     * @return
     */
    public Status addClient(int ID, long time)
    {
    	Status myStatus = getStatusFromCurrent(time);
    	addBall(ID);
    	List<Snack> mySnacks = new ArrayList<Snack>();
    	for (int i = 0; i < MAX_SNACK_SIZE; i++) {
            if (globalSnacks[i].time > status.time)
            	break;
            if (!globalSnacks[i].wasEaten)
                mySnacks.add(globalSnacks[i]);
        }
    	return new Status(myStatus.balls, mySnacks, time - currentTime);
    }
    
    /**
     * 增加球
     * @param ID	服务器指定
     */
    public void addBall(int ID)
    {
    	Ball ball = new Ball(ID,
    			Math.random() * Model.WIDTH,
    			Math.random() * Model.HEIGHT,
    			0, 0, 0, 0,
                Ball.COLORS[snackRandom.nextInt(Snack.COLOR_NUM)],
                0);
    	status.balls.add(ball);
    	++clientNum;
    }
    
    /**
     * 用户退出时给服务器发请求，移除该用户的球
     * @param ID
     */
    public void removeClient(int ID)
    {
    	for (Iterator<ClientAcceleration> acc = clientAccs.iterator(); acc.hasNext();)
    	{
    		ClientAcceleration tmp = (ClientAcceleration)acc.next();
    		if (tmp.ID == ID)
    			acc.remove();
    	}
    	for (Iterator<Ball> b = status.balls.iterator(); b.hasNext();)
    	{
    		Ball tmp = (Ball)b.next();
    		if (tmp.ID == ID)
    			b.remove();
    	}
    	--clientNum;
    }
    /**
     * 计算出这一帧的零食序列（因为数据线程中没有保存这个信息）
     * 用于计算线程的初始化
     * 服务器发起，要求模型计算
     * 模型根据这段时间内客户端上传的加速度信息，将游戏状态更新到当前时间time
     * @param time              time of this frame
     * @return                  status, where ball-status is in last frame while snack-status in this one
     */
    public Status getStatusFromCurrent(long time)
    {
        List<Snack> mySnacks = new ArrayList<>();
        status = new Status(status.balls, mySnacks, time - currentTime);
        
        long timeInterval = time - currentTime - status.time;                //时间间隔（通常为一帧）
        List<Snack> eatenSnacks = new ArrayList<>();
        Collections.sort(status.balls, Comparator.comparingDouble(Ball::getNegativeSize));

        //复制客户端加速度序列并清空，以便继续接受信息而不影响计算
        Deque<ClientAcceleration> tempAcc = new ArrayDeque<>(clientAccs);
        clientAccs.clear();

        //没有收到某个用户的操作，则视为其没有操作
        List<ClientAcceleration> accs = new ArrayList<ClientAcceleration>();
        for (ClientAcceleration move : tempAcc)
            accs.add(move);
        for (Ball b : status.balls) {
            double[] change = {.0, .0};
            for (ClientAcceleration a: accs)
            	if (a.ID == b.ID) {
            		change = a.acc;
                    //之后的操作类似ClientModel
                    b.ax = change[1];
                    b.ay = change[0];
                    b.varyVelocity(new double[]{b.ax * timeInterval, b.ay * timeInterval});
                    b.varyPosition(new double[]{
                            (2 * b.vx - b.ax * timeInterval) * timeInterval / 2,
                            (2 * b.vy - b.ay * timeInterval) * timeInterval / 2
                    });
                    for (Snack s : status.snacks)
                        if (b.isCloseEnough(s)) {
                            status.snacks.remove(s);
                            globalSnacks[s.index].wasEaten = true;
                            eatenSnacks.add(s);
                            b.size += Snack.NUTRITION;
                        }
                }
        }

        int size = status.balls.size();
        boolean[] flag = new boolean[size];
        List<Ball> ballGraveyard = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (flag[i]) continue;
            for (int j = i + 1; j < size; j++) {
                Ball b = status.balls.get(i);
                Ball c = status.balls.get(j);
                if (b.ID != c.ID && b.isCloseEnough(c) && (b.size - c.size) / c.size > 0.1) {
                    ballGraveyard.add(c);
                    flag[j] = true;
                    b.size += c.size;
                }
            }
        }
        status.balls.removeAll(ballGraveyard);
        for (Ball b: ballGraveyard)
        	addBall(b.ID);

        //扫尾
        status = new Status(status.balls, eatenSnacks, time - currentTime);
        return status;
    }

    /**
     * 接收来自客户端的加速度信息
     * @param serverToModel    moves(acceleration) of a client
     */
    public void acceptAcceleration(ServerToModel serverToModel)
    {
    	clientAccs = serverToModel.clientAcclerations;
    }

    public ServerModel(long time)
    {
    	super(time);
    	this.clientNum = 0;
    }
    
    public ServerModel(long time, int clientNum, Status originalStatus)
    {
    	super(time);
        this.clientNum = clientNum;
        status = originalStatus;
    }
}
