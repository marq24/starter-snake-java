package com.battlesnake.starter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class Session {

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    Point pos;
    int len;
    int health;
    int state = 0;
    int tPhase = 0;
    ArrayList<Integer> cmdChain = null;
    boolean enterDangerZone = false;
    int X = -1;
    int Y = -1;
    int[][] enemyBodies = null;
    int[][] enemyNextMovePossibleLocations = null;
    int[][] myBody = null;
    ArrayList<Point> foodPlaces = null;
    boolean patched = false;
    int stateToRestore = -1;
    int Xmin, Ymin, Xmax, Ymax;

    private boolean checkDoomed() {
        boolean ret = false;
        if(cmdChain.size() > 4){
            if(!enterDangerZone){
                enterDangerZone = true;
                int lastCmdToKeep = cmdChain.get(cmdChain.size()-1);
                cmdChain = new ArrayList<>();
                cmdChain.add(lastCmdToKeep);
            }else {
                LOG.error("DOOMED!");
                ret = true;
            }
        }
        return ret;
    }

    private boolean checkCmdChain(int cmdToSearch, int pos){
        int len = cmdChain.size();
        if(len > pos - 1){
            int cmdAtPos = cmdChain.get(len - pos);
            LOG.info("Compare Type: "+cmdChain+" search: "+cmdToSearch+" found: "+cmdAtPos);
            if(cmdAtPos == cmdToSearch){
                return true;
            }
        }
        return false;
    }

    public String checkSpecialMoves(){
        // if we are in the UPPERROW and the x=0 is free, let's move to the LEFT!
        if(tPhase > 0 && pos.y == Ymax && pos.x < Xmax/3){
            if(pos.x > 0) {
                LOG.info("SPECIAL MOVE -> RIGHT CALLED");
                return moveRight(false);
            }else{
                LOG.info("SPECIAL MOVE -> DOWN CALLED");
                return moveDown(false);
            }
        }
        return null;
    }

    public String moveUp(boolean reset) {
        cmdChain.add(Snake.UP);
        if(checkDoomed()){
            return Snake.D;
        }
        logState("UP");
        if (pos.y < Ymax &&
                myBody[pos.y + 1][pos.x] == 0
                && enemyBodies[pos.y + 1][pos.x] == 0
                && (enterDangerZone || enemyNextMovePossibleLocations[pos.y + 1][pos.x] < len)
            //&& (enemyHeads.length < pos.y + 3 || enemyHeads[pos.y + 2][pos.x] < len)
        ) {
            return Snake.U;
        } else {
            // here we can add some special handling if we are in the UPPER ROW, then we might want to move to
            // the LEFT (instead of the right)
            state = Snake.RIGHT;
            if (reset) {
                patched = false;
                Ymax = Y - 1;
                Xmax = X - 1;
            }
            return moveRight(reset);
        }
    }

    public String moveRight(boolean reset) {
        cmdChain.add(Snake.RIGHT);
        if(checkDoomed()){
            return Snake.L;
        }
        logState("RIGHT");
        if (pos.x < Xmax &&
                myBody[pos.y][pos.x + 1] == 0
                && enemyBodies[pos.y][pos.x + 1] == 0
                && (enterDangerZone || enemyNextMovePossibleLocations[pos.y][pos.x + 1] < len)
            //&& (enemyHeads[pos.y].length < pos.x + 3 || enemyHeads[pos.y][pos.x + 2] < len)
        ) {
            return Snake.R;
        } else {
            if (pos.x == Xmax && tPhase > 0) {
                if(pos.y == Ymax){
                    // we should NEVER BE HERE!!
                    // we are in the UPPER/RIGHT Corner while in TraverseMode! (something failed before)
                    LOG.info("WE SHOULD NEVER BE HERE in T-PHASE >0");
                    tPhase = 0;
                    state = Snake.DOWN;
                    return moveDown(reset);
                }else {
                    state = Snake.LEFT;
                    // check if we can MOVE UP?!
                    return moveUp(reset);//U;
                }
            } else {
                state = Snake.DOWN;
                if (reset) {
                    patched = false;
                    Ymax = Y - 1;
                    Xmax = X - 1;
                }
                return moveDown(reset);
            }
        }
    }

    public String moveDown(boolean reset) {
        cmdChain.add(Snake.DOWN);
        if(checkDoomed()){
            return Snake.U;
        }
        logState("DOWN");

        if (pos.y > Ymin &&
                myBody[pos.y - 1][pos.x] == 0
                && enemyBodies[pos.y - 1][pos.x] == 0
                && (enterDangerZone || enemyNextMovePossibleLocations[pos.y - 1][pos.x] < len)
            //&& (pos.y < 2 || enemyHeads[pos.y - 2][pos.x] < len)
        ) {
            if (tPhase == 2 && pos.y == 1) {
                tPhase = 1;
                state = Snake.RIGHT;
                return moveRight(reset);
            } else {
                return Snake.D;
            }
        } else {
            boolean wasRightBefore = checkCmdChain(Snake.RIGHT, 2);
            if (tPhase > 0 && !wasRightBefore) {
                state = Snake.RIGHT;
                if (reset) {
                    patched = false;
                    Ymin = 0;
                    Xmin = 0;
                }
                return moveRight(reset);
            } else {
                if(wasRightBefore){
                    tPhase = 0;
                }
                state = Snake.LEFT;
                if (reset) {
                    patched = false;
                    Ymin = 0;
                    Xmin = 0;
                }
                return moveLeft(reset);
            }
        }
    }

    public String moveLeft(boolean reset) {
        cmdChain.add(Snake.LEFT);
        if(checkDoomed()){
            return Snake.R;
        }
        logState("LEFT");

        boolean canMoveLeft = pos.x > Xmin;
        boolean isSpace = myBody[pos.y][pos.x - 1] == 0
                && enemyBodies[pos.y][pos.x - 1] == 0
                && (enterDangerZone || enemyNextMovePossibleLocations[pos.y][pos.x - 1] < len)
                //&& (pos.x < 2 || enemyHeads[pos.y][pos.x - 2] < len)
                ;

        if (canMoveLeft && (isSpace || tPhase > 0)) {
            if (pos.x == 1) {
                if (pos.y == Ymax) {
                    if (tPhase != 2) {
                        tPhase = 1;
                    }
                    state = Snake.DOWN;
                    // TODO check if we can MOVE LEFT
                    return Snake.L;
                } else {
                    if (tPhase != 2) {
                        tPhase = 1;
                    }
                    state = Snake.RIGHT;
                    // check if we can MOVE UP
                    return moveUp(reset);// U;
                }
            } else {
                if (isSpace) {
                    if (tPhase > 0 && (Ymax - pos.y) % 2 == 1) {
                        tPhase = 2;
                        return moveUp(reset);
                    } else {
                        return Snake.L;
                    }
                } else {
                    return moveUp(reset);
                }
            }
        } else {
            state = Snake.UP;
            if (reset) {
                patched = false;
                Ymin = 0;
                Xmin = 0;
            }
            return moveUp(reset);
        }
    }

    private void logState(final String method) {
        //new Thread(() -> {
            LOG.info(method + " " + tPhase + " "+ enterDangerZone +" {" + cmdChain.toString() + "}");
            LOG.info("____________________");
            for (int y = Ymax; y >= 0; y--) {
                StringBuffer b = new StringBuffer();
                b.append('|');
                for (int x = 0; x < X; x++) {
                    if (pos.x == x && pos.y == y) {
                        b.append("X");
                    } else {
                        if (myBody[y][x] == 1) {
                            b.append('x');
                        } else {
                            if (enemyBodies[y][x] > 0) {
                                if (enemyBodies[y][x] == 1) {
                                    b.append('-');
                                }else{
                                    b.append('+');
                                }
                            } else {
                                if(enemyNextMovePossibleLocations[y][x] > 0){
                                    b.append('o');
                                }else{
                                    b.append(' ');
                                }
                            }
                        }
                    }
                }
                b.append('|');
                LOG.info(b.toString());
            }
            LOG.info("--------------------");
        //}).start();
    }
}
