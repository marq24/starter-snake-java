package com.emb.bs.ite;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Session {

    static final int UNKNOWN = -1;
    static final int UP = 0;
    static final int RIGHT = 1;
    static final int DOWN = 2;
    static final int LEFT = 3;
    static final int DOOMED = 99;

    static String getMoveIntAsString(int move) {
        switch (move) {
            case UP:
                return Snake.U;
            case RIGHT:
                return Snake.R;
            case DOWN:
                return Snake.D;
            case LEFT:
                return Snake.L;
            case DOOMED:
                return "DOOMED";
            default:
                return "UNKNOWN";
        }
    }

    private static final HashMap<Integer, MoveWithState> intMovesToMoveKeysMap = new HashMap<>();
    static{
        intMovesToMoveKeysMap.put(UP, new MoveWithState(UP));
        intMovesToMoveKeysMap.put(RIGHT, new MoveWithState(RIGHT));
        intMovesToMoveKeysMap.put(DOWN, new MoveWithState(DOWN));
        intMovesToMoveKeysMap.put(LEFT, new MoveWithState(LEFT));
    }

    private static final Logger LOG = LoggerFactory.getLogger(Session.class);

    // just for logging...
    ArrayList<String> players;
    String gameId;
    int turn;

    // stateful stuff
    private int tPhase = 0;
    private int state = UP;
    private int mFoodPrimaryDirection = -1;
    private int mFoodSecondaryDirection = -1;
    Point lastTurnTail = null;

    // Food direction stuff
    private Point foodActive = null;
    private boolean foodGoForIt = false;
    private boolean foodFetchConditionGoHazard = false;
    private boolean foodFetchConditionGoBorder = false;

    String LASTMOVE = null;

    Point myHead;
    Point myTail;
    int myLen;
    int myHealth;

    int X = -1;
    int Y = -1;
    private int xMin, yMin, xMax, yMax;

    private boolean doomed = false;
    ArrayList<Point> snakeHeads = null;
    int[][] snakeBodies = null;
    int[][] snakeNextMovePossibleLocations = null;
    HashMap<Point, ArrayList<Integer>> snakeNextMovePossibleLocationList = null;
    int maxOtherSnakeLen = 0;
    int[][] myBody = null;
    int[][] hazardZone = null;
    ArrayList<Point> foodPlaces = null;

    private int MAXDEEP = 0;
    private boolean ignoreOtherTargets = false;
    private boolean enterHazardZone = false;
    private boolean enterBorderZone = false;
    private boolean enterDangerZone = false;
    private boolean enterNoGoZone = false;

    private boolean escapeFromBorder = false;
    private boolean escapeFromHazard = false;


    private boolean mHungerMode = true;

    boolean mWrappedMode = false;
    private boolean mSoloMode = false;
    private boolean mRoyaleMode = false;
    private boolean mConstrictorMode = false;
    private boolean mHazardPresent = false;

    class SavedState {
        int sdState = state;
        int sTPhase = tPhase;
        boolean sEscapeFromBorder = escapeFromBorder;
        boolean sEscapeFromHazard = escapeFromHazard;
        boolean sIgnoreOtherTargets = ignoreOtherTargets;
        boolean sEnterHazardZone = enterHazardZone;
        boolean sEnterBorderZone = enterBorderZone;
        boolean sEnterDangerZone = enterDangerZone;
        boolean sEnterNoGoZone = enterNoGoZone;
        int sMAXDEEP = MAXDEEP;

        @Override
        public String toString() {
                return
                      " st:" + getMoveIntAsString(sdState).substring(0, 2).toUpperCase() + "[" + sdState + "]"
                    + " ph:" + sTPhase
                    + (sEscapeFromHazard ? " GETOUTHAZD" : "")
                    + (mHazardPresent ? " goHazd? " + sEnterHazardZone : "")
                    + " goBorder? " + sEnterBorderZone
                    + (sEscapeFromBorder ? " GAWYBRD" : "")
                    + (sIgnoreOtherTargets ? " IGNOREOTHERS" : "")
                    + " maxDeep? " + sMAXDEEP
                    + " goDanger? " + sEnterDangerZone
                    + " goNoGo? " + sEnterNoGoZone;
        }
    }

    SavedState saveState() {
        return new SavedState();
    }

    void restoreState(SavedState savedState) {
        state = savedState.sdState;
        tPhase = savedState.sTPhase;

        escapeFromBorder = savedState.sEscapeFromBorder;
        escapeFromHazard = savedState.sEscapeFromHazard;
        ignoreOtherTargets = savedState.sIgnoreOtherTargets;
        enterHazardZone = savedState.sEnterHazardZone;
        enterBorderZone = savedState.sEnterBorderZone;
        MAXDEEP = savedState.sMAXDEEP;
        enterDangerZone = savedState.sEnterDangerZone;
        enterNoGoZone = savedState.sEnterNoGoZone;
    }

    class PointWithBool{
        Point point;
        boolean bool;

        public PointWithBool(Point point, boolean bool) {
            this.point = point;
            this.bool = bool;
        }
    }

    static class PointWithInt{
        Point point;
        int val;

        public PointWithInt(Point point, int val) {
            this.point = point;
            this.val = val;
        }
    }

    private void setFullBoardBounds() {
        yMin = 0;
        xMin = 0;
        yMax = Y - 1;
        xMax = X - 1;
    }

    private void restoreBoardBounds(int[] prevBounds) {
        yMin = prevBounds[0];
        xMin = prevBounds[1];
        yMax = prevBounds[2];
        xMax = prevBounds[3];
    }

    private void initSaveBoardBounds() {
        yMin = 1;
        xMin = 1;
        yMax = Y - 2;
        xMax = X - 2;
        enterDangerZone = false;
        enterNoGoZone = false;
        enterBorderZone = false;
        enterHazardZone = false;
        ignoreOtherTargets = false;
    }

    void initSessionForTurn(String gameType, int height, int width) {
        Y = height;
        X = width;
        initSaveBoardBounds();
        doomed = false;
        mHazardPresent = false;

        //firstMoveToTry = -1;
        //cmdChain = new ArrayList<>();

        snakeHeads = new ArrayList<>();
        snakeBodies = new int[Y][X];
        snakeNextMovePossibleLocations = new int[Y][X];
        snakeNextMovePossibleLocationList = new HashMap<>();
        maxOtherSnakeLen = 0;

        myBody = new int[Y][X];

        // TODO: MAXDEEP can be myLen-1 IF THERE IS NO FODD in front of us
        // really???
        MAXDEEP = Math.max(myLen, Y*X/2);//Math.min(len, 20);

        foodGoForIt = false;
        foodFetchConditionGoHazard = false;
        foodFetchConditionGoBorder = false;

        foodPlaces = new ArrayList<>();

        hazardZone = new int[Y][X];

        escapeFromBorder = false;
        escapeFromHazard = false;

        if(gameType != null) {
            switch (gameType) {
                case "standard":
                case "squad":
                    break;

                case "solo":
                    enterBorderZone = true;
                    mHungerMode = false;
                    mSoloMode = true;
                    //setFullBoardBounds();
                    break;

                case "royale":
                    mRoyaleMode = true;
                    mHungerMode = false;
                    break;

                case "wrapped":
                    mWrappedMode = true;
                    enterBorderZone = true;
                    setFullBoardBounds();
                    break;

                case "constrictor":
                    // NOT sure yet, if moving totally
                    // to the border is smart...
                    mConstrictorMode = true;
                    enterBorderZone = true;
                    mHungerMode = false;
                    setFullBoardBounds();
                    break;
            }
        }else{
            // no game mode provided? [do we read from a REPLAY?!]
        }
    }

    void initSessionAfterFullBoardRead(boolean hazardDataIsPresent) {
        // before we check any special moves, we check, if we are already on the borderline, and if this is the
        // case we can/will disable 'avoid borders' flag...

        if (    myHead.y == 0 ||
                myHead.y == Y - 1 ||
                myHead.x == 0 ||
                myHead.x == X - 1
        ) {
            escapeFromBorder = true;
        }

        mHazardPresent = hazardDataIsPresent;
        if(mHazardPresent) {
            if (hazardZone[myHead.y][myHead.x] > 0 && myHealth < 95) {
                escapeFromHazard = true;
            }

            // try to adjust the MIN/MAX values based on the present hazardData...
            if(mRoyaleMode){
                ArrayList<Boolean>[] yAxisHazards = new ArrayList[Y];
                ArrayList<Boolean>[] xAxisHazards = new ArrayList[X];

                for (int y = 0; y < Y; y++) {
                    for (int x = 0; x < X; x++) {
                        if(hazardZone[y][x] == 1){
                            if(yAxisHazards[y] == null){
                                yAxisHazards[y] = new ArrayList<>(Y);
                            }
                            yAxisHazards[y].add(true);

                            if(xAxisHazards[x] == null){
                                xAxisHazards[x] = new ArrayList<>(X);
                            }
                            xAxisHazards[x].add(true);
                        }
                    }
                }

                for (int y = 0; y < yAxisHazards.length; y++) {
                    if(yAxisHazards[y] != null && yAxisHazards[y].size() == Y){
                        if(y < Y/2){
                            yMin = y + 1;
                        }else if(y> Y/2){
                            yMax = y - 1;
                            break;
                        }
                    }
                }

                for (int x = 0; x < xAxisHazards.length; x++) {
                    if(xAxisHazards[x] != null && xAxisHazards[x].size() == X){
                        if(x < X/2){
                            xMin = x + 1;
                        }else if(x > X/2){
                            xMax = x - 1;
                            break;
                        }
                    }
                }
                LOG.info("For: Tn:"+turn+ "-> ADJUSTED MIN/MAX cause of HAZARD TO Y:"+yMin+"-"+yMax+" and X:"+xMin+"-"+xMax);
            }
        }else{
            // there is no hazard  so we can skip the check in the array...
            enterHazardZone = true;
            escapeFromHazard = false;
        }
    }

    private void multiplyHazardThreadsInMap(){
        // POPULATE HAZARD DAMAGE...
        for(int k=0; k < (Math.max(X, Y) / 2) + 1; k++) {
            for (int y = 0; y < Y; y++) {
                for (int x = 0; x < X; x++) {
                    if (hazardZone[y][x] > k) {
                        try {
                            boolean b0 = hazardZone[y + 1][x - 1] > k;
                            boolean b1 = hazardZone[y + 0][x - 1] > k;
                            boolean b2 = hazardZone[y - 1][x - 1] > k;

                            boolean b3 = hazardZone[y + 1][x + 0] > k;
                            boolean b4 = hazardZone[y - 1][x + 0] > k;

                            boolean b5 = hazardZone[y + 1][x + 1] > k;
                            boolean b6 = hazardZone[y + 0][x + 1] > k;
                            boolean b7 = hazardZone[y - 1][x + 1] > k;
                            if (b0 && b1 && b2 && b3 && b4 && b5 && b6 && b7) {
                                hazardZone[y][x]++;
                            }
                        } catch (IndexOutOfBoundsException e) {
                        }
                    }
                }
            }
        }
    }

    private class RiskState {
        boolean endReached = false;
        boolean retry = true;

        private void next(){
            retry = true;
            if (escapeFromBorder) {
                LOG.debug("deactivate ESCAPE-FROM-BORDER");
                escapeFromBorder = false;
            } else if(escapeFromHazard){
                LOG.debug("deactivate ESCAPE-FROM-HAZARD");
                escapeFromHazard = false;
            } else if (!enterBorderZone) {
                LOG.debug("activate now GO-TO-BORDERS");
                enterBorderZone = true;
                setFullBoardBounds();
            } else if (mHazardPresent && !enterHazardZone) {
                LOG.debug("activate now GO-TO-HAZARD");
                enterHazardZone = true;
            } else if(MAXDEEP > 1) {
                LOG.debug("activate MAXDEEP TO: " + MAXDEEP);
                if(MAXDEEP <= Math.max(myLen/1.2, 2) && !ignoreOtherTargets){
                    ignoreOtherTargets = true;
                    LOG.debug("activate IGNOREOTHERS (TARGETS) and reset MAXDEEP from:" + MAXDEEP+" to:"+myLen);
                    MAXDEEP = Math.max(myLen, Y*X/2);
                } else {
                    MAXDEEP--;
                }
            } else if (!enterDangerZone) {
                LOG.debug("activate now GO-TO-DANGER-ZONE");
                enterDangerZone = true;
            } else if (!enterNoGoZone) {
                LOG.debug("activate now GO-TO-NO-GO-ZONE");
                enterNoGoZone = true;
            } else {
                LOG.debug("NO-WAY-TO-MOVE");
                endReached = true;
                retry = false;
            }
        }
    }

    private int moveDirection(int move, RiskState risk) {
        if(risk == null){
            risk = new RiskState();
        }else{
            risk.next();
        }
        if (risk.endReached) {
            return DOOMED;
        } else if (risk.retry) {
            //logState(moveAsString);
            boolean canMove = false;

            switch (move){
                case UP:
                    canMove = canMoveUp();
                    break;
                case RIGHT:
                    canMove = canMoveRight();
                    break;
                case DOWN:
                    canMove = canMoveDown();
                    break;
                case LEFT:
                    canMove = canMoveLeft();
                    break;
            }
            if (canMove) {
                LOG.debug(getMoveIntAsString(move)+": YES");
                return move;
            }else{
                LOG.debug(getMoveIntAsString(move)+": NO");
                return moveDirection(move, risk);
            }
        }
        return UNKNOWN;
    }

    private int getAdvantage(){
        if(mHungerMode){
            return 8;
        } else {
            // how many foods-ahead we want to be...
            // is "one" really just enough?
            int advantage = 2;
            if (myLen > 19) {
                advantage++;
            }
            if (myLen > 24) {
                advantage++;
            }
            if (myLen > 29) {
                advantage++;
            }
            if (myLen > 39) {
                advantage++;
            }
            return advantage;
        }
    }

    private List<Integer> checkSpecialMoves() {
        /*List<Integer> killMoves = checkKillMoves();
        if(killMoves != null && killMoves.size() >0){
            LOG.info("FOUND possible KILLs :" +killMoves);
        }*/

        if (myHealth < 41 || (myLen - getAdvantage() <= maxOtherSnakeLen)) {
            LOG.info("Check for FOOD! health:" + myHealth + " len:" + myLen +"(-"+getAdvantage()+")"+ "<=" + maxOtherSnakeLen);
            // ok we need to start to fetch FOOD!
            // we should move into the direction of the next FOOD! (setting our preferred direction)
            checkFoodMoves();
        }else{
            // need to reset all food parameters...
            resetFoodStatus();
        }
        return null;//killMoves;
    }

    private List<Integer> checkKillMoves(){
        // verify if this IF condition makes sense here - we might want to decide later, IF we are going to
        // make the killMove...
        ArrayList<Integer> checkedKills = new ArrayList<>();
        checkForPossibleKillInDirection(UP, checkedKills);
        checkForPossibleKillInDirection(RIGHT, checkedKills);
        checkForPossibleKillInDirection(DOWN, checkedKills);
        checkForPossibleKillInDirection(LEFT, checkedKills);
        return checkedKills;
    }

    private void checkForPossibleKillInDirection(int move, ArrayList<Integer> resList) {
        Point p = getNewPointForDirection(myHead, move);
        try {
            if(myBody[p.y][p.x] == 0 && snakeBodies[p.y][p.x] == 0) {
                int val = snakeNextMovePossibleLocations[p.y][p.x];
                if (val > 0 && val < myLen) {
                    resList.add(move);
                }
            }
        }catch(IndexOutOfBoundsException e){
            // TODO: check for indexOutOfBounds (p.y/x can be -1 or > Y/X) - right now catching the exception
            // cause of laziness
        }
    }

    private void checkFoodMoves() {
        Point closestFood = null;

        // we remove all food's that are in direct area of other snakes heads
        // I don't want to battle for food with others (now)
        ArrayList<Point> availableFoods = new ArrayList<>(foodPlaces.size());
        availableFoods.addAll(foodPlaces);

        if (myHealth > 25) {
            // in wrappedMode there are no corners... and in the first 5 turns we might pick
            // up food that is around us...
            if(turn > 20 && !mWrappedMode) {
                // food in CORNERS is TOXIC (but if we are already IN the corner we will
                // take it!
                if (!(myHead.x == 0 && myHead.y <= 1) || (myHead.x <= 1 && myHead.y == 0)) {
                    availableFoods.remove(new Point(0, 0));
                }
                if (!(myHead.x == X - 1 && myHead.y <= 1) || (myHead.x <= X - 2 && myHead.y == 0)) {
                    availableFoods.remove(new Point(0, X - 1));
                }
                if (!(myHead.x == 0 && myHead.y <= Y - 2) || (myHead.x <= 1 && myHead.y == Y - 1)) {
                    availableFoods.remove(new Point(Y - 1, 0));
                }
                if (!(myHead.x == X - 1 && myHead.y >= Y - 2) || (myHead.x >= X - 2 && myHead.y == Y - 1)) {
                    availableFoods.remove(new Point(Y - 1, X - 1));
                }
            }
            for (Point h : snakeHeads) {
                // food that is head of another snake that is longer or has
                // the same length should be ignored...
                if (snakeBodies[h.y][h.x] >= myLen){
                    availableFoods.remove(new Point(h.y + 1, h.x + 0));
                    availableFoods.remove(new Point(h.y + 0, h.x + 1));
                    availableFoods.remove(new Point(h.y - 1, h.x + 0));
                    availableFoods.remove(new Point(h.y + 0, h.x - 1));
                }
            }
        }

        TreeMap<Integer, ArrayList<Point>> foodTargetsByDistance = new TreeMap<>();
        for (Point f : availableFoods) {
            int dist = getPointDistance(f, myHead);
            if(!isLocatedAtBorder(f) || dist < 3 || (dist < 4 && myHealth < 65) || myHealth < 51) {
                boolean addFoodAsTarget = true;
                for (Point h : snakeHeads) {
                    int otherSnakesDist = getPointDistance(f, h);
                    boolean otherIsStronger = snakeBodies[h.y][h.x] >= myLen;
                    if(dist < ((X+Y)/2) && (dist > otherSnakesDist || (dist == otherSnakesDist && otherIsStronger))) {
                        addFoodAsTarget = false;
                        break;
                    }
                }
                if(addFoodAsTarget){
                    ArrayList<Point> foodsInDist = foodTargetsByDistance.get(dist);
                    if(foodsInDist == null){
                        foodsInDist = new ArrayList<>();
                        foodTargetsByDistance.put(dist, foodsInDist);
                    }
                    foodsInDist.add(f);
                }
            }
        }

        if(foodTargetsByDistance.size() > 0){
            // get the list of the closest food...
            ArrayList<Point> closestFoodList = foodTargetsByDistance.firstEntry().getValue();
            if(closestFoodList.size() == 1){
                // cool only one
                closestFood = closestFoodList.get(0);
            } else {
                // ok we have to decide which of the foods in the same distance can be caught
                // most easily
                int minBlocks = Integer.MAX_VALUE;

                // ok take the first as default...
                closestFood = closestFoodList.get(0);

                // TODO: count blockingBlocks in WRAPPED MODE
                if(!mWrappedMode) {
                    // need to decided which food is better?!
                    for (Point cfp : closestFoodList) {
                        int blocks = countBlockingsBetweenFoodAndHead(cfp);
                        minBlocks = Math.min(minBlocks, blocks);
                        if (minBlocks == blocks) {
                            closestFood = cfp;
                        } else {
                            LOG.info("FOOD at " + cfp + " blocked by " + blocks + " - stay on: " + closestFood + "(blocked by " + minBlocks + ")");
                        }
                    }
                }
            }

            if(foodActive == null || !foodActive.equals(closestFood)){
                mFoodPrimaryDirection = -1;
                mFoodSecondaryDirection = -1;
            }
            foodActive = closestFood;

            int yDelta = myHead.y - closestFood.y;
            int xDelta = myHead.x - closestFood.x;
            int preferredYDirection = -1;
            int preferredXDirection = -1;
            if (mFoodPrimaryDirection == -1 || yDelta == 0 || xDelta == 0) {
                if(mWrappedMode && Math.abs(yDelta) > Y/2) {
                    preferredYDirection = UP;
                } else if (yDelta > 0) {
                    preferredYDirection = DOWN;
                } else if (yDelta < 0) {
                    preferredYDirection = UP;
                }

                if((mWrappedMode && Math.abs(xDelta) > X/2)){
                    preferredXDirection = RIGHT;
                }else if (xDelta > 0) {
                    preferredXDirection = LEFT;
                } else if (xDelta < 0){
                    preferredXDirection = RIGHT;
                }

                if (Math.abs(yDelta) > Math.abs(xDelta)) {
                    mFoodPrimaryDirection = preferredYDirection;
                    mFoodSecondaryDirection = preferredXDirection;
                } else {
                    mFoodPrimaryDirection = preferredXDirection;
                    mFoodSecondaryDirection = preferredYDirection;
                }
            }

            foodGoForIt = true;
            // IF we are LOW on health, and HAZARD is enabled - we skip the hazard check!
            boolean goFullBorder = false;
            if(!enterHazardZone || escapeFromHazard) {
                // two move in hazard takes 2 x 16 health (we need at least 32 health left)
                if ((myHealth < 34 || (mRoyaleMode && myHealth < 80))
                        &&  (   (xDelta == 0 && Math.abs(yDelta) <= 2) ||
                                (yDelta == 0 && Math.abs(xDelta) <= 2) ||
                                (Math.abs(yDelta) == 1 && Math.abs(xDelta) == 1)
                            )
                ) {
                    foodFetchConditionGoHazard = true;
                    goFullBorder = true;
                }
            }
            if (!enterBorderZone || escapeFromBorder) {
                // 1) when GoHazard triggered...
                // 2) for the first 30 turns we can stay on border...
                // 3) when our length is smaller than 15
                // 4) when we are one smaller than the largest other
                // 5) when the food we want to fetch is at BORDER
                //if(goFullBorder || turn < 30 || myLen < 15 || myLen - 1 < maxOtherSnakeLen || isLocatedAtBorder(closestFood)){
                if(goFullBorder || turn < 30 || isLocatedAtBorder(closestFood)){
                    foodFetchConditionGoBorder = true;
                }
            }

            if(mFoodSecondaryDirection != -1){
                LOG.info("TRY TO GET FOOD: at: " + closestFood + " moving: " + getMoveIntAsString(mFoodPrimaryDirection) +" or "+getMoveIntAsString(mFoodSecondaryDirection));
            }else {
                LOG.info("TRY TO GET FOOD: at: " + closestFood + " moving: " + getMoveIntAsString(mFoodPrimaryDirection));
            }
        } else {
            resetFoodStatus();
            LOG.info("NO NEARBY FOOD FOUND "+foodTargetsByDistance+" ["+foodPlaces+"]");
        }
    }
    private void resetFoodStatus() {
        foodGoForIt = false;
        foodFetchConditionGoHazard = false;
        foodFetchConditionGoBorder = false;
        foodActive = null;
        mFoodPrimaryDirection = -1;
        mFoodSecondaryDirection = -1;
    }

    private int getPointDistance(Point p1, Point p2){
        if(!mWrappedMode){
            return Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y);
        }else{
            // in wrappedMode: if p1.x = 0 & p2.x = 11, then distance is 1
            return Math.min(Math.abs(p1.x + X - p2.x), Math.abs(p1.x - p2.x)) + Math.min(Math.abs(p1.y + Y - p2.y), Math.abs(p1.y - p2.y));
        }
    }

    private int getPointXDistance(Point p1, Point p2){
        if(!mWrappedMode){
            return Math.abs(p1.x - p2.x);
        }else{
            // in wrappedMode: if p1.x = 0 & p2.x = 11, then distance is 1
            return Math.min(Math.abs(p1.x + X - p2.x), Math.abs(p1.x - p2.x));
        }
    }

    private int getPointYDistance(Point p1, Point p2){
        if(!mWrappedMode){
            return Math.abs(p1.y - p2.y);
        }else{
            // in wrappedMode: if p1.x = 0 & p2.x = 11, then distance is 1
            return Math.min(Math.abs(p1.y + Y - p2.y), Math.abs(p1.y - p2.y));
        }
    }



    private int countBlockingsBetweenFoodAndHead(Point cfp) {
        try {
            int blocks = 0;
            int yDelta = myHead.y - cfp.y;
            int xDelta = myHead.x - cfp.x;
            if (Math.abs(yDelta) > Math.abs(xDelta)) {
                if (yDelta > 0) {
                    // we need to go DOWN to the food...
                    for (int i = cfp.y + 1; i < myHead.y; i++) {
                        if (myBody[i][myHead.x] > 0 || snakeBodies[i][myHead.x] > 0) {
                            blocks++;
                        }
                    }
                } else {
                    // we need to go UP to the food...
                    for (int i = myHead.y + 1; i < cfp.y; i++) {
                        if (myBody[i][myHead.x] > 0 || snakeBodies[i][myHead.x] > 0) {
                            blocks++;
                        }
                    }
                }
            } else {
                if (xDelta > 0) {
                    // we need to go LEFT to the food...
                    for (int i = cfp.x + 1; i < myHead.x; i++) {
                        if (myBody[myHead.y][i] > 0 || snakeBodies[myHead.y][i] > 0) {
                            blocks++;
                        }
                    }
                } else {
                    // we need to go RIGHT to the food...
                    for (int i = myHead.x + 1; i < cfp.x; i++) {
                        if (myBody[myHead.y][i] > 0 || snakeBodies[myHead.y][i] > 0) {
                            blocks++;
                        }
                    }
                }
            }
            return blocks;
        }catch(IndexOutOfBoundsException e){
            LOG.error("IoB when try to count blocking... ");
            return Integer.MAX_VALUE;
        }
    }

    private boolean isLocatedAtBorder(Point p) {
        if(turn < 21 || mWrappedMode){
            return  false;//hazardNearbyPlaces.contains(p);
        }else {
            if(turn < 50 || myLen < 15 || myLen - 1 < maxOtherSnakeLen){
                return  p.y == 0
                        || p.y == Y - 1
                        || p.x == 0
                        || p.x == X - 1
                        //|| hazardNearbyPlaces.contains(p)
                        ;
            }else {
                return  p.y <= yMin
                        || p.y >= yMax
                        || p.x <= xMin
                        || p.x >= xMax
                        //|| hazardNearbyPlaces.contains(p)
                        ;
            }
        }
    }

    Point getNewPointForDirection(Point aPos, int move){
        Point newPos = aPos.clone();
        if(mWrappedMode) {
            switch (move) {
                case UP:
                    newPos.y = (newPos.y + 1) % Y;
                    break;
                case RIGHT:
                    newPos.x = (newPos.x + 1) % X;
                    break;
                case DOWN:
                    newPos.y = (newPos.y - 1 + Y) % Y;//newPos.y > 0 ? newPos.y - 1 : Y - 1;
                    break;
                case LEFT:
                    newPos.x = (newPos.x -1 + X) % X;//newPos.x > 0 ? newPos.x - 1 : X - 1;
                    break;
            }
        }else{
            switch (move) {
                case UP:
                    newPos.y++;
                    break;
                case RIGHT:
                    newPos.x++;
                    break;
                case DOWN:
                    newPos.y--;
                    break;
                case LEFT:
                    newPos.x--;
                    break;
            }
        }
        return newPos;
    }

    private boolean willCreateLoop(int move, Point aPos, int[][] finalMap, int count) {
        // OK we have to check, if with the "planed" next move we will create a closed loop structure (either
        // with ourselves, with the border or with any enemy...
        // when we reach our own tail, then we will fit into the hole for sure!
        try {
            count++;
            if(count <= MAXDEEP) {
                Point newPos = getNewPointForDirection(aPos, move);
                if(lastTurnTail != null && newPos.equals(lastTurnTail) && !foodPlaces.contains(newPos)){
                    return false;
                }
                    // simple check, if we can move from the new position to any other location

                // so in the finalMap we have the picture of the MOVE RESULT
                if(finalMap == null) {
                    finalMap = new int[Y][X];
                    finalMap[myHead.y][myHead.x] = 1;
                    for (int y = 0; y < X; y++) {
                        for (int x = 0; x < X; x++) {
                            if(lastTurnTail != null && lastTurnTail.y == y && lastTurnTail.x == x) {
                                finalMap[y][x] = 2; // the GOLDEN ASS
                            }else if (myBody[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (snakeBodies[y][x] > 0) {
                                finalMap[y][x] = 1;
                            } else if (!ignoreOtherTargets && snakeNextMovePossibleLocations[y][x] > 0) {
                                finalMap[y][x] = 1;
                            }
                        }
                    }
                }
                finalMap[newPos.y][newPos.x] = 1;

//if(turn == 32){logMap(finalMap, count);}

                boolean noUP = !canMoveUp(newPos, finalMap, count);
                boolean noDW = !canMoveDown(newPos, finalMap, count);
                boolean noLF = !canMoveLeft(newPos, finalMap, count);
                boolean noRT = !canMoveRight(newPos, finalMap, count);

                if (noUP && noDW && noLF && noRT) {
                    return true;
                }
            }

        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ willCreateLoop " + getMoveIntAsString(move) + " check...", e);
        }
        return false;
    }

    private boolean canMoveUp() {
        try {
            if (escapeFromBorder && (myHead.x == 0 || myHead.x == X - 1)) {
                return false;
            } else {
                int newY = (myHead.y + 1) % Y;
                return  (mWrappedMode || myHead.y < yMax)
                        && myBody[newY][myHead.x] == 0
                        && snakeBodies[newY][myHead.x] == 0
                        && (!escapeFromHazard || hazardZone[newY][myHead.x] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[newY][myHead.x] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[newY][myHead.x] < myLen)
                        && (enterNoGoZone || !willCreateLoop(UP, myHead, null,0));
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveUp check...", e);
            return false;
        }
    }

    private boolean canMoveUp(Point aPos, int[][] map, int c) {
        try {
            int newY = (aPos.y + 1) % Y;
            return  (mWrappedMode || aPos.y < yMax)
                    && (map[newY][aPos.x] == 0 || map[newY][aPos.x] == 2)
                    && (enterNoGoZone || !willCreateLoop(UP, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveUpLoop check...", e);
            return false;
        }
    }

    private boolean canMoveRight() {
        try {
            if (escapeFromBorder && (myHead.y == 0 || myHead.y == Y - 1)) {
                return false;
            } else {
                int newX = (myHead.x + 1) % X;
                return  (mWrappedMode || myHead.x < xMax)
                        && myBody[myHead.y][newX] == 0
                        && snakeBodies[myHead.y][newX] == 0
                        && (!escapeFromHazard || hazardZone[myHead.y][newX] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[myHead.y][newX] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[myHead.y][newX] < myLen)
                        && (enterNoGoZone || !willCreateLoop(RIGHT, myHead, null, 0))
                        ;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveRight check...", e);
            return false;
        }
    }

    private boolean canMoveRight(Point aPos, int[][] map, int c) {
        try {
            int newX = (aPos.x + 1) % X;
            return  (mWrappedMode || aPos.x < xMax)
                    && (map[aPos.y][newX] == 0 || map[aPos.y][newX] == 2)
                    && (enterNoGoZone || !willCreateLoop(RIGHT, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveRightLoop check...", e);
            return false;
        }
    }

    private boolean canMoveDown() {
        try {
            if (escapeFromBorder && (myHead.x == 0 || myHead.x == X - 1)) {
                return false;
            } else {
                int newY = (myHead.y - 1 + Y) % Y;//myPos.y > 0 ? myPos.y - 1 : Y - 1;
                return  (mWrappedMode || myHead.y > yMin)
                        && myBody[newY][myHead.x] == 0
                        && snakeBodies[newY][myHead.x] == 0
                        && (!escapeFromHazard || hazardZone[newY][myHead.x] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[newY][myHead.x] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[newY][myHead.x] < myLen)
                        && (enterNoGoZone || !willCreateLoop(DOWN, myHead, null, 0))
                        ;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveDown check...", e);
            return false;
        }
    }

    private boolean canMoveDown(Point aPos, int[][] map, int c) {
        try {
            int newY = (aPos.y - 1 + Y) % Y; // aPos.y > 0 ? aPos.y - 1 : Y - 1;
            return  (mWrappedMode || aPos.y > yMin)
                    && (map[newY][aPos.x] == 0 || map[newY][aPos.x] == 2)
                    && (enterNoGoZone || !willCreateLoop(DOWN, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveDownLoop check...", e);
            return false;
        }
    }

    private boolean canMoveLeft() {
        try {
            if (escapeFromBorder && (myHead.y == 0 || myHead.y == Y - 1)) {
                return false;
            } else {
                int newX = (myHead.x - 1 + X) % X;//myPos.x > 0 ? myPos.x - 1 : X-1;
                return  (mWrappedMode || myHead.x > xMin)
                        && myBody[myHead.y][newX] == 0
                        && snakeBodies[myHead.y][newX] == 0
                        && (!escapeFromHazard || hazardZone[myHead.y][newX] == 0)
                        && (enterHazardZone || myHealth > 96 || hazardZone[myHead.y][newX] == 0)
                        && (enterDangerZone || snakeNextMovePossibleLocations[myHead.y][newX] < myLen)
                        && (enterNoGoZone || !willCreateLoop(LEFT, myHead, null, 0))
                        ;
            }
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveLeft check...", e);
            return false;
        }
    }

    private boolean canMoveLeft(Point aPos, int[][] map, int c) {
        try {
            int newX = (aPos.x - 1 + X) % X;//aPos.x > 0 ? aPos.x - 1 : X-1;
            return  (mWrappedMode || aPos.x > xMin)
                    && (map[aPos.y][newX] == 0 || map[aPos.y][newX] == 2)
                    && (enterNoGoZone || !willCreateLoop(LEFT, aPos, map, c))
                    ;
        } catch (IndexOutOfBoundsException e) {
            LOG.info("IoB @ canMoveLeftLoop check...", e);
            return false;
        }
    }

    private void logState(final String method) {
        logState(method, LOG);
    }

    void logState(String msg, Logger LOG) {
        msg = msg
                + " Tn:" + turn
                + " st:" + getMoveIntAsString(state).substring(0, 2).toUpperCase() + "[" + state + "]"
                + " ph:" + tPhase
                + (escapeFromHazard ? " GETOUTHAZD" : "")
                + (mHazardPresent ? " goHazd? " + enterHazardZone : "")
                + " goBorder? " + enterBorderZone
                + (escapeFromBorder ? " GAWYBRD" : "")
                + (ignoreOtherTargets ? " IGNOREOTHERS" : "")
                + " maxDeep? " + MAXDEEP
                + " goDanger? " + enterDangerZone
                + " goNoGo? " + enterNoGoZone
                + " "+gameId;
        LOG.info(msg);
    }

    void logBoard(Logger LOG) {

        StringBuffer z = new StringBuffer();
        z.append(" ┌");
        for(int i=0; i< X; i++){z.append('─');}
        z.append("┐");
        LOG.info(z.toString());

        for (int y = Y - 1; y >= 0; y--) {
            StringBuffer b = new StringBuffer();
            b.append(y % 10);
            b.append('│');
            for (int x = 0; x < X; x++) {
                if (myHead.x == x && myHead.y == y) {
                    b.append("X");
                } else if(lastTurnTail !=null && lastTurnTail.x == x && lastTurnTail.y == y){
                    b.append('y');
                } else if (myBody[y][x] == 1) {
                    b.append('c');
                } else if (snakeBodies[y][x] > 0) {
                    if (snakeBodies[y][x] == 1) {
                        b.append('+');
                    } else {
                        b.append('O');
                    }
                } else {
                    boolean isHazard = hazardZone[y][x] > 0;
                    boolean isFoodPlace = foodPlaces.contains(new Point(y, x));
                    if (snakeNextMovePossibleLocations[y][x] > 0) {
                        if (isFoodPlace) {
                            b.append('●');
                        } else {
                            b.append('◦');
                        }
                    } else if (isFoodPlace) {
                        if (isHazard) {
                            b.append('▓');
                        } else {
                            b.append('*');
                        }
                    } else if (isHazard) {
                        b.append('▒');
                    } else {
                        b.append(' ');
                    }
                }
            }
            b.append('│');
            LOG.info(b.toString());
        }

        StringBuffer y = new StringBuffer();
        y.append(" └");
        for(int i=0; i< X; i++){y.append('─');}
        y.append("┘");
        LOG.info(y.toString());

        StringBuffer b = new StringBuffer();
        b.append("  ");
        for (int i = 0; i < X; i++) {
            b.append(i % 10);
        }
        LOG.info(b.toString());
    }

    private void logMap(int[][] aMap, int c) {
        LOG.info("XXL TurnNo:"+turn+" MAXDEEP:"+MAXDEEP+" len:"+ myLen +" loopCount:"+c);
        StringBuffer z = new StringBuffer();
        z.append(" ┌");
        for(int i=0; i< X; i++){z.append('─');}
        z.append('┐');
        LOG.info(z.toString());

        for (int y = Y - 1; y >= 0; y--) {
            StringBuffer b = new StringBuffer();
            b.append(y % 10);
            b.append('│');
            for (int x = 0; x < X; x++) {
                if(aMap[y][x]>0){
                    b.append('X');
                }else{
                    b.append(' ');
                }
            }
            b.append('│');
            LOG.info(b.toString());
        }
        StringBuffer y = new StringBuffer();
        y.append(" └");
        for(int i=0; i< X; i++){y.append('─');}
        y.append('┘');
        LOG.info(y.toString());
        StringBuffer b = new StringBuffer();
        b.append("  ");
        for (int i = 0; i < X; i++) {
            b.append(i % 10);
        }
        LOG.info(b.toString());
    }

    /*ArrayList<Integer> cmdChain = null;
    int firstMoveToTry = -1;
    public String moveUp() {
        if (cmdChain.size() < 4 && cmdChain.contains(UP)) {
            // here we can generate randomness!
            return moveRight();
        } else {
            logState("UP");
            if (canMoveUp()) {
                LOG.debug("UP: YES");
                return Snake.U;
            } else {
                LOG.debug("UP: NO");
                // can't move...
                if (myPos.x < xMax / 2 || cmdChain.contains(LEFT)) {
                    state = RIGHT;
                    //LOG.debug("UP: NO - check RIGHT x:" + pos.x + " < Xmax/2:"+ xMax/2);
                    return moveRight();
                } else {
                    state = LEFT;
                    //LOG.debug("UP: NO - check LEFT");
                    return moveLeft();
                }
            }
        }
    }

    public String moveRight() {
        if (cmdChain.size() < 4 && cmdChain.contains(RIGHT)) {
            return moveDown();
        } else {
            logState("RI");
            if (canMoveRight()) {
                LOG.debug("RIGHT: YES");
                return Snake.R;
            } else {
                LOG.debug("RIGHT: NO");
                // can't move...
                if (myPos.x == xMax && tPhase > 0) {
                    if (myPos.y == yMax) {
                        // we should NEVER BE HERE!!
                        // we are in the UPPER/RIGHT Corner while in TraverseMode! (something failed before)
                        LOG.info("WE SHOULD NEVER BE HERE in T-PHASE >0");
                        tPhase = 0;
                        state = DOWN;
                        return moveDown();
                    } else {
                        state = LEFT;
                        return moveUp();
                    }
                } else {
                    return decideForUpOrDownUsedFromMoveLeftOrRight(RIGHT);
                }
            }
        }
    }

    public String moveDown() {
        if (cmdChain.size() < 4 && cmdChain.contains(DOWN)) {
            return moveLeft();
        } else {
            logState("DO");
            if (canMoveDown()) {
                LOG.debug("DOWN: YES");
                if (goForFood) {
                    return Snake.D;
                } else {
                    if (tPhase == 2 && myPos.y == yMin + 1) {
                        tPhase = 1;
                        state = RIGHT;
                        return moveRight();
                    } else {
                        return Snake.D;
                    }
                }
            } else {
                LOG.debug("DOWN: NO");
                // can't move...
                if (tPhase > 0) {
                    state = RIGHT;
                    return moveRight();
                } else {
                    if (myPos.x < xMax / 2 || cmdChain.contains(LEFT)) {
                        state = RIGHT;
                        return moveRight();
                    } else {
                        state = LEFT;
                        return moveLeft();
                    }
                }
            }
        }
    }
    public String moveLeft() {
        if (cmdChain.size() < 4 && cmdChain.contains(LEFT)) {
            return moveUp();
        } else {
            logState("LE");
            if (canMoveLeft()) {
                LOG.debug("LEFT: YES");
                if (goForFood) {
                    return Snake.L;
                } else {
                    // even if we "could" move to left - let's check, if we should/will follow our program...
                    if (myPos.x == xMin + 1) {
                        // We are at the left-hand "border" side of the board
                        if (tPhase != 2) {
                            tPhase = 1;
                        }
                        if (myPos.y == yMax) {
                            //LOG.debug("LEFT: STATE down -> RETURN: LEFT");
                            state = DOWN;
                            return Snake.L;
                        } else {
                            if (canMoveUp()) {
                                //LOG.debug("LEFT: STATE right -> RETURN: UP");
                                state = RIGHT;
                                return moveUp();
                            } else {
                                //LOG.debug("LEFT: RETURN: LEFT");
                                return Snake.L;
                            }
                        }
                    } else {
                        if ((yMax - myPos.y) % 2 == 1) {
                            // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                            // we simply really move to the LEFT (since we can!))
                            if (canMoveUp()) {
                                tPhase = 2;
                                return moveUp();
                            } else {
                                return Snake.L;
                            }
                        } else {
                            return Snake.L;
                        }
                    }
                }
            } else {
                // can't move...
                LOG.debug("LEFT: NO");
                // IF we can't go LEFT, then we should check, if we are at our special position
                // SEE also 'YES' part (only difference is, that we do not MOVE to LEFT here!)
                if (myPos.x == xMin + 1) {
                    // We are at the left-hand "border" side of the board
                    if (tPhase != 2) {
                        tPhase = 1;
                    }
                    if (myPos.y == yMax) {
                        state = DOWN;
                        //return Snake.L;
                        return moveDown();

                    } else {
                        if (canMoveUp()) {
                            state = RIGHT;
                            return moveUp();
                        } else {
                            //return Snake.L;
                            return moveDown();
                        }
                    }
                } else {
                    if ((yMax - myPos.y) % 2 == 1) {
                        // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                        // we simply really move to the LEFT (since we can!))
                        if (canMoveUp()) {
                            tPhase = 2;
                            return moveUp();
                        } else {
                            //return Snake.L;
                            return moveDown();
                        }
                    } else {
                        //return Snake.L;
                        // if we are in the pending mode, we prefer to go ALWAYS UP
                        return decideForUpOrDownUsedFromMoveLeftOrRight(LEFT);
                    }
                }
            }
        }
    }
    private String decideForUpOrDownUsedFromMoveLeftOrRight(int cmd) {
        // if we are in the pending mode, we prefer to go ALWAYS-UP
        if (tPhase > 0 && !cmdChain.contains(UP) && myPos.y < yMax) {
            state = UP;
            return moveUp();
        } else {
            if (myPos.y < yMax / 2 || cmdChain.contains(DOWN)) {
                state = UP;
                return moveUp();
            } else {
                state = DOWN;
                return moveDown();
            }
        }
    }*/

    private static int[] options = new int[]{UP, RIGHT, DOWN, LEFT};
    MoveWithState calculateNextMoveOptions() {
        int[] currentActiveBounds = new int[]{yMin, xMin, yMax, xMax};
        // checkSpecialMoves will also activate the 'goForFood' flag - so if this flag is set
        // we hat a primary and secondary direction in which we should move in order to find/get
        // food...

        // TODO: WARN - WE NEED TO ENABLE THIS AGAIN!!!
        List<Integer> killMoves = /*null;/*/checkSpecialMoves();

        /*SortedSet<Integer> options = new TreeSet<Integer>();
        // make sure that we check initially our preferred direction...
        if(foodGoForIt) {
            if (mFoodPrimaryDirection != -1) {
                options.add(mFoodPrimaryDirection);
            }
            if (mFoodSecondaryDirection != -1) {
                options.add(mFoodSecondaryDirection);
            }
        }
        options.add(state);
        options.add(UP);
        options.add(RIGHT);
        options.add(DOWN);
        options.add(LEFT);*/

        ArrayList<MoveWithState> possibleMoves = new ArrayList<>();
        Session.SavedState startState = saveState();
        for(int possibleDirection: options){
            restoreState(startState);
            restoreBoardBounds(currentActiveBounds);

            if(possibleDirection == mFoodPrimaryDirection || possibleDirection == mFoodSecondaryDirection){
                // So depending on the situation we want to take more risks in order to
                // get FOOD - but this Extra risk should be ONLY applied when making a
                // food move!
                if(foodFetchConditionGoHazard){
                    escapeFromHazard = false;
                    enterHazardZone = true;
                }
                if(foodFetchConditionGoBorder){
                    escapeFromBorder = false;
                    enterBorderZone = true;
                    setFullBoardBounds();
                }
            }
            // ok checking the next direction...
            int moveResult = moveDirection(possibleDirection, null);
            if(moveResult != UNKNOWN && moveResult != DOOMED) {
                MoveWithState move = new MoveWithState(moveResult, this);
                possibleMoves.add(move);
                LOG.info("EVALUATED WE can MOVE: " + move);
            }else{
                LOG.info("EVALUATED "+getMoveIntAsString(possibleDirection)+" FAILED");
            }
        }

        // once we got out of our loop we need to reset the active board bounds... so that min/max bounds
        // can be used in the getBestMove() code...
        restoreBoardBounds(currentActiveBounds);

        if(possibleMoves.size() == 0){
            doomed = true;
            // TODO Later - check, if any of the moves make still some sense?! [but I don't think so]
            LOG.error("***********************");
            LOG.error("DOOMED!");
            LOG.error("***********************");
            return new MoveWithState(DOOMED, this);
        }

if(Snake.debugTurn == turn){
    LOG.debug("HALT");
}

        if(possibleMoves.size() == 1){
            return possibleMoves.get(0);
        }else{
            return getBestMove(possibleMoves, killMoves);
        }
    }

    private MoveWithState getBestMove(ArrayList<MoveWithState> possibleMoves, List<Integer> killMoves) {
        // ok we have plenty of alternative moves...
        // we should check, WHICH of them is the most promising...


        /*int moveFromPlanX = tryFollowMovePlan(possibleMoves);
        if (moveFromPlanX != UNKNOWN) {
            MoveWithState routeMove = intMovesToMoveKeysMap.get(moveFromPlanX);
            return possibleMoves.get(possibleMoves.indexOf(routeMove));
        }*/


        //1) only keep the moves with the highest DEEP...
        int maxDeptWithOtherTargets = 0;
        int maxDeptWithoutOtherTargets = 0;
        for (MoveWithState aMove : possibleMoves) {
            int dept = aMove.state.sMAXDEEP;
            if(aMove.state.sIgnoreOtherTargets){
                maxDeptWithoutOtherTargets = Math.max(maxDeptWithoutOtherTargets, dept);
            }else{
                maxDeptWithOtherTargets = Math.max(maxDeptWithOtherTargets, dept);
            }
        }

        int maxDept = 0;
        boolean removeIgnoreOtherTargets = true;
        // there are moves that can be more promising (if the other snakes does not move into our way)
        if(maxDeptWithoutOtherTargets > 0) {
            if(maxDeptWithOtherTargets == 0){
                // ok we are in the situation, that all the possible moves have the "IGNORE OTHER MOVE"
                // flag...
                maxDept = maxDeptWithoutOtherTargets;
                removeIgnoreOtherTargets = false;
            } else if(maxDeptWithOtherTargets >= maxDeptWithoutOtherTargets) {
                maxDept = maxDeptWithOtherTargets;
                removeIgnoreOtherTargets = true;
            } else if(maxDeptWithOtherTargets >= myLen){
                maxDept = maxDeptWithOtherTargets;
                removeIgnoreOtherTargets = true;
            } else if(maxDeptWithoutOtherTargets > myLen && maxDeptWithoutOtherTargets > maxDeptWithOtherTargets){
                // so ignoring th other targets will give use at least the possibility to survive
                maxDept = maxDeptWithoutOtherTargets;
                removeIgnoreOtherTargets = false;
            } else{
                // we are "nearly doomed" anyhow - so what ever move we are going to choose we will not have
                // a chance to fit into the remaining space...
                maxDept = Math.min(maxDeptWithoutOtherTargets, maxDeptWithOtherTargets);
                removeIgnoreOtherTargets = false;
            }
        }else{
            // all possible moves can be made consider the moves of the other snakes as well...
            maxDept = maxDeptWithOtherTargets;
            removeIgnoreOtherTargets = true;
        }

        maxDept = Math.min(maxDept, (int) (myLen*1.4));

        // do finally the filtering...
        ArrayList<MoveWithState> keepOnlyWithHighDeep = new ArrayList<>(possibleMoves);
        for (MoveWithState aMove : possibleMoves) {
            int dept = aMove.state.sMAXDEEP;
            if (dept < maxDept) {
                keepOnlyWithHighDeep.remove(aMove);
            }else if(removeIgnoreOtherTargets && aMove.state.sIgnoreOtherTargets){
                keepOnlyWithHighDeep.remove(aMove);
            }
        }
        if(keepOnlyWithHighDeep.size()>0) {
            possibleMoves = keepOnlyWithHighDeep;
            // ok only one option left - so let's use this...
            if (possibleMoves.size() == 1) {
                return possibleMoves.get(0);
            }
        }

        //2) remove all "toDangerous" moves (when we have better alternatives)
        boolean aMoveHasEscapeFromHazard = false;
        boolean aMoveHasEscapeFromBorder = false;
        boolean keepGoDanger = true;
        boolean keepGoNoGo = true;
        for (MoveWithState aMove : possibleMoves) {
            if (keepGoNoGo && !aMove.state.sEnterNoGoZone) {
                keepGoNoGo = false;
            }
            if (keepGoDanger && !aMove.state.sEnterDangerZone) {
                keepGoDanger = false;
            }

            // escape values will be used later in the code!
            if(!aMoveHasEscapeFromHazard){
                aMoveHasEscapeFromHazard = aMove.state.sEscapeFromHazard;
            }
            if(!aMoveHasEscapeFromBorder){
                aMoveHasEscapeFromBorder = aMove.state.sEscapeFromBorder;
            }
        }

        // do finally the filtering...
        ArrayList<MoveWithState> keepOnlyWithLowRisk = new ArrayList<>(possibleMoves);
        for (MoveWithState aMove : possibleMoves) {
            if (!keepGoNoGo && aMove.state.sEnterNoGoZone){
                keepOnlyWithLowRisk.remove(aMove);
            }
            if (!keepGoDanger && aMove.state.sEnterDangerZone){
                keepOnlyWithLowRisk.remove(aMove);
            }
        }
        if(keepOnlyWithLowRisk.size() > 0) {
            possibleMoves = keepOnlyWithLowRisk;
            // ok only one option left - so let's use this...
            if (possibleMoves.size() == 1) {
                return possibleMoves.get(0);
            }
        }

        if(mFoodPrimaryDirection != -1 && mFoodSecondaryDirection != -1){
            // TODO: decide for the better FOOD move...
            // checking if primary or secondary FOOD direction is possible
            // selecting the MOVE with less RISK (if there is one with)
            // avoid from border we can do so...
            MoveWithState priMove = intMovesToMoveKeysMap.get(mFoodPrimaryDirection);
            int priIdx = possibleMoves.indexOf(priMove);
            if(priIdx > -1){
                priMove = possibleMoves.get(priIdx);
            }else{
                priMove = null;
            }
            MoveWithState secMove = intMovesToMoveKeysMap.get(mFoodSecondaryDirection);
            int secIdx = possibleMoves.indexOf(secMove);
            if(secIdx > -1){
                secMove = possibleMoves.get(secIdx);
            }else{
                secMove = null;
            }

            if(secMove != null && priMove != null){
                // Compare possible distance to other's (to compare which is less risky)

                if( (secMove.state.sEscapeFromHazard && !priMove.state.sEscapeFromHazard)
                ||  (secMove.state.sEscapeFromBorder && !priMove.state.sEscapeFromBorder)
                ||  (!secMove.state.sEnterBorderZone && priMove.state.sEnterBorderZone)
                ||  (!secMove.state.sEnterHazardZone && priMove.state.sEnterHazardZone)
                ||  (!secMove.state.sEnterDangerZone && priMove.state.sEnterDangerZone)
                ){
                    // prefer secondary!
                    state = secMove.move;
                    return secMove;
                }else{
                    state = priMove.move;
                    return priMove;
                }
            } else if(priMove != null){
                state = priMove.move;
                return priMove;
            } else if(secMove != null){
                // if we can catch our TAIL, then
                if(turn > 250 && myHealth > 50){
                    MoveWithState tailCatchMove = checkForCatchOwnTail(possibleMoves);
                    if (tailCatchMove != null) return tailCatchMove;
                }
                // and only if we can't catch out tail. we make the second direction move...
                state = secMove.move;
                return secMove;
            }
        } else if(mFoodPrimaryDirection != -1) {
            MoveWithState pMove = intMovesToMoveKeysMap.get(mFoodPrimaryDirection);
            if (possibleMoves.contains(pMove)) {
                return possibleMoves.get(possibleMoves.indexOf(pMove));
            }
        }

        // 3) Manual additional risk calculation...
        // comparing RISK of "move" with alternative moves

        // 1'st filtering for least dangerous locations...
        TreeMap<Integer, ArrayList<MoveWithState>> groupByDirectThread = new TreeMap<>();
        for (MoveWithState aMove : possibleMoves) {
            Point resultingPos = aMove.getResPosForMyHead(this);
            // checking if we are under direct threat
            int aMoveRisk = snakeNextMovePossibleLocations[resultingPos.y][resultingPos.x];
            if(aMoveRisk > 0){
                if(aMoveRisk < myLen){
                    aMoveRisk = 0;
                } else if(aMoveRisk == myLen){
                    aMoveRisk = 1;
                }
            }
            ArrayList<MoveWithState> moves = groupByDirectThread.get(aMoveRisk);
            if(moves == null) {
                moves = new ArrayList<>();
                groupByDirectThread.put(aMoveRisk, moves);
            }
            moves.add(aMove);
        }
        ArrayList<MoveWithState> bestList = groupByDirectThread.firstEntry().getValue();
        if(bestList.size() == 1){
            return bestList.get(0);
        }

        // 2a - checking if we can catch our own tail?!
        // in this case we can ignore the approach of other snake heads
        // but only if this will not move into hazard
        MoveWithState tailCatchMove = checkForCatchOwnTail(bestList);
        if (tailCatchMove != null) return tailCatchMove;

        ArrayList<MoveWithState> bestListNoHzd = null;
        // we should check the result list's (at least the first two ones), if there
        // will be a MOVE to Border or Move to Hazard implied (when trying to get away
        // from other sneak heads - and DECIDE for an alternative?!)
        if(mHazardPresent){
            bestListNoHzd = new ArrayList<>(bestList);
            for(MoveWithState aBestMove: bestList){
                if(aBestMove.state.sEnterHazardZone){
                    bestListNoHzd.remove(aBestMove);
                }
            }
        }

        if(!mConstrictorMode){
            // 2'nd checking the remaining moves which brings us closer to another snake's head...
            ArrayList<PointWithBool> dangerousNextMovePositions = new ArrayList<>();
            for (Point otherSnakeResultingPos: snakeNextMovePossibleLocationList.keySet()) {
                boolean canPickUpFood = foodPlaces.contains(otherSnakeResultingPos);
                int otherLen = snakeNextMovePossibleLocations[otherSnakeResultingPos.y][otherSnakeResultingPos.x];
                if (otherLen >= myLen || (canPickUpFood && otherLen + 1 >= myLen)) {
                    // ok here we have another snake location, that cen be dangerous for us
                    boolean sameLen = otherLen == myLen || (canPickUpFood && otherLen + 1 == myLen);
                    dangerousNextMovePositions.add(new PointWithBool(otherSnakeResultingPos, sameLen));
                }
            }

            TreeMap<Integer, ArrayList<MoveWithState>> groupByOtherHeadDistance = groupByOtherHeadDistance(bestList, dangerousNextMovePositions);
            // groupByOtherHeadDistance is sorted by distance to thread - so LARGER is BETTER!!!
            Map.Entry<Integer, ArrayList<MoveWithState>> bestEntry = groupByOtherHeadDistance.lastEntry();
            bestList = bestEntry.getValue();

if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
            TreeMap<Integer, ArrayList<MoveWithState>> groupByOtherHeadsWithoutEnterHzd = null;
            if(bestListNoHzd != null && bestListNoHzd.size() > 0) {
                groupByOtherHeadsWithoutEnterHzd = groupByOtherHeadDistance(bestListNoHzd, dangerousNextMovePositions);
                Map.Entry<Integer, ArrayList<MoveWithState>> bestEntryNoHzd = groupByOtherHeadsWithoutEnterHzd.lastEntry();
                bestListNoHzd = bestEntryNoHzd.getValue();

                // ok checking the content of the NO-HAZARD-List with the open list...
                if (bestList.containsAll(bestListNoHzd)) {
                    // ok cool - all the possible BEST-Moves also include the NONE-HZRD moves...
                    // so we reduce the BEST-List to the none-HAZARD moves
                    bestList = bestListNoHzd;
                } else {
                    if(bestEntry.getKey() - bestEntryNoHzd.getKey() == 1){
                        ArrayList<MoveWithState> secondBestList = groupByOtherHeadDistance.get(bestEntryNoHzd.getKey());
                        if(secondBestList.containsAll(bestListNoHzd)){
                            bestList = bestListNoHzd;
                        }
                    }
                }
            }
        }else{
           // DO NOTHING concerning DANGER-SNEAK Heads in mConstrictorMode
        }

        // if the is already only ome option left..
        if(bestList.size() == 1){
            MoveWithState aMove = bestList.get(0);
        }


if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
        // checking if one of the moves will be a possible move target for TWO snakes! (if this is the case, then
        // this will typically end in a corner!
        ArrayList<MoveWithState> noneDoubleTragets = new ArrayList<>(bestList);
        boolean modifiedList = false;
        for(MoveWithState aMove: bestList){
            Point resPoint = aMove.getResPosForMyHead(this);
            if(snakeNextMovePossibleLocationList.containsKey(resPoint)){
                ArrayList<Integer> list = snakeNextMovePossibleLocationList.get(resPoint);
                if(list.size() > 1){
                    noneDoubleTragets.remove(aMove);
                    modifiedList = true;
                }
            }
        }
        if(modifiedList && noneDoubleTragets.size() > 0){
            bestList = noneDoubleTragets;
            if(bestList.size() == 1){
                MoveWithState aMove = bestList.get(0);
                if(!mHazardPresent || !aMove.state.sEnterHazardZone){
                    return aMove;
                }
            }
        }


        if(foodGoForIt && foodActive != null){
if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
            TreeMap<Integer, ArrayList<MoveWithState>> groupByResultingFoodDistance = new TreeMap<>();
            for (MoveWithState aMove : bestList) {
                Point resPos = aMove.getResPosForMyHead(this);
                int foodDistance = getPointDistance(foodActive, resPos);
                ArrayList<MoveWithState> list = groupByResultingFoodDistance.get(foodDistance);
                if(list == null){
                    list = new ArrayList<>();
                    groupByResultingFoodDistance.put(foodDistance, list);
                }
                list.add(aMove);
            }
            bestList = groupByResultingFoodDistance.firstEntry().getValue();
            if(bestList.size() == 1){
                return bestList.get(0);
            }

            // check if on of the moves is the opposite food direction?
            if(mFoodPrimaryDirection != -1) {
                ArrayList<MoveWithState> clone = new ArrayList<>(bestList);
                for (MoveWithState aMove : bestList) {
                    if(isOpposite(mFoodPrimaryDirection, aMove.move)){
                        clone.remove(aMove);
                    }
                }
                if(clone.size()>0){
                    bestList = clone;
                }
                if(bestList.size() == 1){
                    MoveWithState aMove = bestList.get(0);
                    if(!mHazardPresent || !aMove.state.sEnterHazardZone){
                        return aMove;
                    }
                }
            }
        }

        if(!mWrappedMode){
            TreeMap<Integer, ArrayList<MoveWithState>> groupByEnterBorderZone = new TreeMap<>();
            for (MoveWithState aMove : bestList) {
                Point resultingPos = aMove.getResPosForMyHead(this);
                int aMoveRisk = 0;
                // TODO: not only the BORDER - also the MIN/MAX can be not so smart...
                if (resultingPos.y == 0 || resultingPos.y == Y - 1) {
                    aMoveRisk++;
                }
                if (resultingPos.x == 0 || resultingPos.x == X - 1) {
                    aMoveRisk++;
                }

                ArrayList<MoveWithState> moves = groupByEnterBorderZone.get(aMoveRisk);
                if(moves == null) {
                    moves = new ArrayList<>();
                    groupByEnterBorderZone.put(aMoveRisk, moves);
                }
                moves.add(aMove);
            }
            bestList = groupByEnterBorderZone.firstEntry().getValue();
        }

if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
        if(bestList.size() == 1){
            return bestList.get(0);
        }

        // check if some moves have the "go border" active (and others not)
        boolean goToBorder = true;
        for(MoveWithState aMove: bestList){
            if(goToBorder) {
                goToBorder = aMove.state.sEnterBorderZone;
            }else{
                break;
            }
        }

        // not all entries have go-to-border...
        if(!goToBorder){
            ArrayList<MoveWithState> movesWithoutGoToBorder = new ArrayList<>(bestList);
            for(MoveWithState aMove: bestList){
                if(aMove.state.sEnterBorderZone){
                    // keeping the kill moves!!! [even if they are goToBorder=true]
                    if(killMoves == null || !killMoves.contains(aMove.move)){
                        movesWithoutGoToBorder.remove(aMove);
                    }
                }
            }
            if(movesWithoutGoToBorder.size() > 0) {
                bestList = movesWithoutGoToBorder;
                // ok - only one option left... let's return that!
                if(bestList.size() == 1){
                    return bestList.get(0);
                }
            }
        }

if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
        // comparing the hazard moves by its thread level
        if(mHazardPresent){
            multiplyHazardThreadsInMap();

            TreeMap<Integer, ArrayList<MoveWithState>> groupByHazardLevel = new TreeMap<>();
            for (MoveWithState aMove : bestList) {
                Point resPos = aMove.getResPosForMyHead(this);
                int hzdLevel = hazardZone[resPos.y][resPos.x];
                ArrayList<MoveWithState> list = groupByHazardLevel.get(hzdLevel);
                if(list == null){
                    list = new ArrayList<>();
                    groupByHazardLevel.put(hzdLevel, list);
                }
                list.add(aMove);
            }
            bestList = groupByHazardLevel.firstEntry().getValue();
            if(bestList.size() == 1){
                return bestList.get(0);
            }
        }

        // ok if all the remaining moves have the same (low) "risk", we can check,
        // if there are possible "killMoves"...
        if(killMoves != null){
            for(Integer aKillMove: killMoves){
                MoveWithState finalKillMove = intMovesToMoveKeysMap.get(aKillMove);
                if(bestList.contains(finalKillMove)){
                    int idxOfKillMove = bestList.indexOf(finalKillMove);
                    LOG.info("GO for a possible KILL -> " +getMoveIntAsString(aKillMove));
                    return bestList.get(idxOfKillMove);
                }
            }
        }

        // checking if there is a GETAWAY from HAZARD or BORDER
        if(aMoveHasEscapeFromHazard) {
            for (MoveWithState aMove : bestList) {
                if (aMove.state.sEscapeFromHazard) {
                    return aMove;
                }
            }
        }
        if(aMoveHasEscapeFromBorder) {
            for (MoveWithState aMove : bestList) {
                if (aMove.state.sEscapeFromBorder) {
                    return aMove;
                }
            }
        }

        // cool to 'still' have so many options...
        if (bestList.size() == 2) {
            int move1 = bestList.get(0).move;
            int move2 = bestList.get(1).move;
            if(isOpposite(move1, move2)) {
                // OK - UP/DOWN or LEFT/RIGHT
                int[][] finalMap = new int[Y][X];
                finalMap[myHead.y][myHead.x] = 1;
                for (int y = 0; y < X; y++) {
                    for (int x = 0; x < X; x++) {
                        if (myBody[y][x] > 0) {
                            finalMap[y][x] = 1;
                        } else if (snakeBodies[y][x] > 0) {
                            finalMap[y][x] = 1;
                        } else if (snakeNextMovePossibleLocations[y][x] > 0) {
                            finalMap[y][x] = 1;
                        }
                    }
                }

                //logMap(finalMap, 0);

                boolean toRestore = enterNoGoZone;
                enterNoGoZone = true;
                int op1 = countMoves(finalMap, myHead, move1, 0) - 1;
                int op2 = countMoves(finalMap, myHead, move2, 0) - 1;
                enterNoGoZone = toRestore;

                if(op1>op2){
                    return bestList.get(0);
                }else if(op2>op1){
                    return bestList.get(1);
                }
            }
        }

if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}

        // checking the default movement options from our initial implemented movement plan...
        if(true){//mSoloMode) {
            // as fallback take the first entry from our list...
            MoveWithState finalPossibleFallbackMove = bestList.get(0);

            int moveFromPlan = tryFollowMovePlan(bestList);
            if (moveFromPlan != UNKNOWN) {
                MoveWithState routeMove = intMovesToMoveKeysMap.get(moveFromPlan);
                return bestList.get(bestList.indexOf(routeMove));
            } else {
                return finalPossibleFallbackMove;
            }
        }else{
            // ok still options - then we prefer to move to the center?
            return bestList.get((int) (bestList.size() * Math.random()));
        }
    }

    private MoveWithState checkForCatchOwnTail(ArrayList<MoveWithState> moveList) {
        if(lastTurnTail != null){
            for (MoveWithState aMove : moveList) {
                if((!mHazardPresent || !aMove.state.sEnterHazardZone) && !aMove.state.sEnterBorderZone){
                    Point resultingPos = aMove.getResPosForMyHead(this);
                    // cool - just lat pick that one!
                    if (resultingPos.equals(lastTurnTail)) {
                        return aMove;
                    }
                }
            }

            // second run...
            for (MoveWithState aMove : moveList) {
                if((!mHazardPresent || !aMove.state.sEnterHazardZone) && !aMove.state.sEnterBorderZone){
                    Point resultingPos = aMove.getResPosForMyHead(this);
                    // cool - just lat pick that one!
                    if(getPointDistance(myTail, resultingPos) == 1 && !foodPlaces.contains(resultingPos)){
                        return aMove;
                    }
                }
            }

        }
        return null;
    }

    private int countMoves(int[][] map, Point aPos, int move, int count) {
        Point nextPoint = getNewPointForDirection(aPos, move);
        // to skip loop check!
        switch (move){
            case UP:
                if(count>Y){
                    return count;
                }else {
                    if (canMoveUp(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;

            case DOWN:
                if(count>Y){
                    return count;
                }else {
                    if (canMoveDown(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;

            case LEFT:
                if(count>X){
                    return count;
                }else {
                    if (canMoveLeft(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;

            case RIGHT:
                if(count>X){
                    return count;
                }else {
                    if (canMoveRight(aPos, map, 0)) {
                        count = countMoves(map, nextPoint, move, count);
                    }
                }
                break;
        }
        return ++count;
    }

    private boolean isOpposite(int i, int j) {
        return  (i==UP && j==DOWN) ||
                (j==UP && i==DOWN) ||
                (i==LEFT && j==RIGHT) ||
                (j==LEFT && i==RIGHT)
                ;
    }

    private TreeMap<Integer, ArrayList<MoveWithState>> groupByOtherHeadDistance(ArrayList<MoveWithState> bestList, ArrayList<PointWithBool> dangerousNextMovePositions) {
if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
        TreeMap<Integer, ArrayList<MoveWithState>> returnMap = new TreeMap<>();
        for (MoveWithState aMove : bestList) {
            Point resultingPos = aMove.getResPosForMyHead(this);
            int minDist = 100;
            for(PointWithBool otherSnake: dangerousNextMovePositions) {
                int minEvalDistance = 3;

                // otherSnake.bool => snake have the same length then we do
                if(otherSnake.bool){
                    minEvalDistance = 2;
                }

                int faceToFaceDist = getPointDistance(otherSnake.point, resultingPos);
                if(faceToFaceDist == 2){
                    // will this end in a CAN BE CATCHED in NEXT Move?!
                    if(getPointXDistance(otherSnake.point, resultingPos) == 1){
                        faceToFaceDist = 1;
                    }
                }
                if (faceToFaceDist < minEvalDistance) {
                    minDist = Math.min(minDist, faceToFaceDist);
                }
            }

            ArrayList<MoveWithState> moves = returnMap.get(minDist);
            if(moves == null) {
                moves = new ArrayList<>();
                returnMap.put(minDist, moves);
            }
            moves.add(aMove);
        }
if(Snake.debugTurn == turn){
    LOG.debug("HALT" + bestList);
}
        return returnMap;
    }

    private int tryFollowMovePlan(ArrayList<MoveWithState> finalMoveOptions) {
        LOG.info("follow our path... (cause no other priority could be found)");
        // now we can check, if we can follow the default movement plan...
        // for all the possible MOVE directions we might have to set our BoardBounds?!
        boolean canGoUp     = finalMoveOptions.contains(intMovesToMoveKeysMap.get(UP));
        boolean canGoRight  = finalMoveOptions.contains(intMovesToMoveKeysMap.get(RIGHT));
        boolean canGoDown   = finalMoveOptions.contains(intMovesToMoveKeysMap.get(DOWN));
        boolean canGoLeft   = finalMoveOptions.contains(intMovesToMoveKeysMap.get(LEFT));

        switch (state){
            case UP:
                if(canGoUp) {
                    return UP;
                } else {
                    if (myHead.x < xMax / 2 || !canGoLeft){ //cmdChain.contains(LEFT)) {
                        state = RIGHT;
                        if(canGoRight){
                            return RIGHT;
                        }
                    } else {
                        state = LEFT;
                        if(canGoLeft){
                            return LEFT;
                        }
                    }
                }
                break;

            case RIGHT:
                if(canGoRight) {
                    return RIGHT;
                }else{
                    if (myHead.x == xMax && tPhase > 0) {
                        if (canGoDown && myHead.y == yMax) {
                            // we should NEVER BE HERE!!
                            // we are in the UPPER/RIGHT Corner while in TraverseMode! (something failed before)
                            LOG.info("WE SHOULD NEVER BE HERE in T-PHASE >0");
                            tPhase = 0;
                            state = DOWN;
                            return DOWN;
                        } else {
                            state = LEFT;
                            //OLD CODE:
                            //return moveUp();
                            // NEW
                            if(canGoUp){
                                return UP;
                            }
                        }
                    } else {
                        // NEW CODE... [when we are in the init phase - reached lower right corner
                        // we go to lower left corner]
                        if(myHead.y == yMin && tPhase == 0 && canGoLeft){
                            state = LEFT;
                            return LEFT;
                        }else {
                            int upOrDown = decideForUpOrDownUsedFromMoveLeftOrRight(canGoUp, canGoDown);
                            if(upOrDown > UNKNOWN) {
                                return upOrDown;
                            }
                        }
                    }
                }
                break;

            case DOWN:
                if(canGoDown){
                    if (canGoRight && tPhase == 2 && myHead.y == yMin + 1) {
                        tPhase = 1;
                        state = RIGHT;
                        return RIGHT;
                    } else {
                        return DOWN;
                    }
                } else{
                    if (canGoRight && tPhase > 0) {
                        state = RIGHT;
                        return RIGHT;
                    } else {
                        if (canGoRight && (myHead.x < xMax / 2 || !canGoLeft)) { //cmdChain.contains(LEFT)) {
                            state = RIGHT;
                            return RIGHT;
                        } else if(canGoLeft){
                            state = LEFT;
                            return LEFT;
                        }else{
                            // looks lke we can't go LEFT or RIGHT...
                            // and we CAN NOT GO DOWN :-/
                        }
                    }
                }
                break;

            case LEFT:
                if(canGoLeft) {

                    // even if we "could" move to left - let's check, if we should/will follow our program...
                    if (myHead.x == xMin + 1) {
                        // We are at the left-hand "border" side of the board
                        if (tPhase != 2) {
                            tPhase = 1;
                        }
                        if (myHead.y == yMax) {
                            state = DOWN;
                            return LEFT;
                        } else {
                            if (canGoUp) {
                                state = RIGHT;
                                return UP;
                            } else {
                                return LEFT;
                            }
                        }
                    } else if ((yMax - myHead.y) % 2 == 1) {
                        // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                        // we simply really move to the LEFT (since we can!))
                        if (canGoUp) {
                            tPhase = 2;
                            return UP;
                        } else {
                            return LEFT;
                        }
                    } else {
                        return LEFT;
                    }

                } else {

                    // IF we can't go LEFT, then we should check, if we are at our special position
                    // SEE also 'YES' part (only difference is, that we do not MOVE to LEFT here!)
                    if (myHead.x == xMin + 1) {
                        // We are at the left-hand "border" side of the board
                        if (tPhase != 2) {
                            tPhase = 1;
                        }
                        if (myHead.y == yMax) {
                            state = DOWN;
                            //return Snake.L;
                            //OLD CODE:
                            //return moveDown();
                            // NEW
                            if (canGoDown) {
                                return DOWN;
                            }else{
                                // TODO ?!
                            }
                        } else {
                            if (canGoRight) {
                                state = RIGHT;
                                return RIGHT;
                            } else if (canGoUp) {
                                state = RIGHT;
                                return UP;
                            }
                        }
                    }else if(myHead.x == xMax){
                        if (canGoLeft){
                            state = LEFT;
                            return LEFT;
                        }else if (canGoUp) {
                            state = LEFT;
                            return UP;
                        }

                    } else {
                        if ((yMax - myHead.y) % 2 == 1) {
                            // before we instantly decide to go up - we need to check, IF we can GO UP (and if not,
                            // we simply really move to the LEFT (since we can!))
                            if (canGoUp) {
                                tPhase = 2;
                                return UP;
                            } else {
                                //return Snake.L;
                                //OLD CODE:
                                //return moveDown();

                                // NEW
                                if(canGoDown){
                                    return DOWN;
                                }
                            }
                        } else {
                            // return Snake.L;
                            // if we are in the pending mode, we prefer to go ALWAYS UP
                            int upOrDown = decideForUpOrDownUsedFromMoveLeftOrRight(canGoUp, canGoDown);
                            if(upOrDown > UNKNOWN) {
                                return upOrDown;
                            }
                        }
                    }
                }
                break;
        }
        return UNKNOWN;
    }

    private int decideForUpOrDownUsedFromMoveLeftOrRight(boolean canGoUp, boolean canGoDown) {
        // if we are in the pending mode, we prefer to go ALWAYS-UP
        if (tPhase > 0 && canGoUp && myHead.y < yMax) {
            state = UP;
            return UP;
        } else {
            if (canGoUp && (myHead.y < yMax / 2 || !canGoDown)) {
                state = UP;
                return UP;
            } else if (canGoDown){
                state = DOWN;
                return DOWN;
            }
        }
        return UNKNOWN;
    }
}