
        @Subscribe
        public void onGameTick(GameTick tick)
        {
            idleTimeSecs();
            checkMovementIdle();

            if(client.getLocalPlayer().getAnimation()==-1){
                idle = true;
            }
            else{idle = false;}



        private static final Set<Integer> TARGET_NPC_IDS = ImmutableSet.of(NpcID.COW, NpcID.COW_5842, NpcID.COW_CALF, NpcID.COW_CALF_2794,
                NpcID.COW_CALF_2801, NpcID.COW_2791, NpcID.COW_2793, NpcID.COW_2795, NpcID.COW_6401);

        @Getter
        private List<NPC> NPCList = new ArrayList<>();

        private Random random = new Random();

        private int random2(int lowerLimit, int upperLimit){
            int rand2;
            if(lowerLimit == upperLimit){return rand2 = 0;} else
                rand2 = random.nextInt(upperLimit-lowerLimit) + lowerLimit;
            return rand2;
        }

        private double random2Dbl(double lowerLimit, double upperLimit){
            return (lowerLimit + (upperLimit - lowerLimit) * random.nextDouble());}


        private int MenuIndex (String TargetMenuOption)
        {
            if (client.getMenuEntries()==null){return 0;}
            MenuEntry menuOptions[] = client.getMenuEntries();
            client.getWidgetPositionsX();
            int menuSize = menuOptions.length;
            int optionFromBottom = 0;
            int optionIndex;
            for (MenuEntry option : menuOptions)
            {
                if (option.getOption().matches(TargetMenuOption)){
                    optionIndex = menuSize - optionFromBottom;
                    return optionIndex;
                }
                optionFromBottom = optionFromBottom+1;
            }
            return 0;
        }

        private Point MenuIndexPosition (int MenuIndex,Point LastRightClick)
        {
            int RCStartY = LastRightClick.getY();
            int RCStartX = LastRightClick.getX();
            int baseYOffset = 27;
            int rowOffset = 15;
            int xTolerance = 35;
            int yTolerance = 4;
            int menuY = RCStartY + baseYOffset + (MenuIndex-1)*rowOffset + random2(-yTolerance,yTolerance);
            int menuX = RCStartX + random2(-xTolerance,xTolerance);
            Point MenuIndexPoint = new Point (menuX,menuY);
            return MenuIndexPoint;
        }



        private void addon() throws AWTException, InterruptedException {
            int HP = client.getRealSkillLevel(Skill.HITPOINTS);
            if(foodInventory()==0){return;}
            int HPThreshold = 9;
            while (HP<HPThreshold){eatFood();}
            List<NPC> localTarget = new ArrayList<>();
            List<NPC> listClone = new ArrayList<>(client.getNpcs());
            //System.out.println(" | check 1 | ");
            if(randomNPCCheck()!=null){
                dismissRandom();
            }
            double ii = (double) i;
            double factorCheck = ii/50;
            if( factorCheck == (int)factorCheck ){
                //if(BreakTimer()>360){
                System.out.print(" || SLEEPING || ");
                {try {  Thread.sleep(random2(60000,120000));  } catch (InterruptedException e) { e.printStackTrace(); }}}
            //System.out.println(" | check 2 | ");
            while(!checkMovementIdle()){Thread.sleep(1000);}
            //System.out.println(" | check 3 | ");
            if(foodInventory()>0 && Equipped()){
                WorldPoint TargetArea = new WorldPoint(random2(3254,3258),random2(3282,3286),0);
                //System.out.println(" | check 4 | ");
                if(Math.abs(client.getLocalPlayer().getWorldLocation().getX()-TargetArea.getX())>10 ||
                        Math.abs(client.getLocalPlayer().getWorldLocation().getY()-TargetArea.getY())>10
                ){
                    //System.out.println(" | check 5 | ");
                    walk(TargetArea);
                    return;
                }
                //System.out.print(" | check 6 | ");
                for (NPC npc : listClone) {
                    int npcId = npc.getId();
                    //System.out.println(" |      treespots  .  " + object.getId() + " , " + object.getWorldLocation());
                    if (COW_IDS.contains(npcId) && npc.getWorldLocation().distanceTo2D(TargetArea) <= 7 &&
                            worldToCanvas(npc.getWorldLocation()).getX()>8 && worldToCanvas(npc.getWorldLocation()).getX()<1620
                            && worldToCanvas(npc.getWorldLocation()).getY()>180 && worldToCanvas(npc.getWorldLocation()).getY()<810 &&
                            idleTimeSecs()>3 && npc.getAnimation()==-1)
                    { localTarget.add(npc); }}
                System.out.println(" |      local trees  .  " + localTarget.size());
                if (localTarget.size() != 0 && idleTimeSecs()>3 ){
                    localTarget = inverseSortTreeDistanceFromPlayer(localTarget);

                    Point spotPerspective = worldToCanvas(localTarget.get(0).getWorldLocation());
                    // need canvas check here and iterate
                    Robot robot = new Robot();
                    moveMouse(robot,client.getMouseCanvasPosition(),spotPerspective,10,5,5);
                    leftClick(robot);
                    return;}
                if (idleTimeSecs()<=3 ){
                    int randomInt = random2(1,10);
                    if (randomInt == 5)	{RandomMouseMove();}
                    return;}
                if (localTarget.size() == 0 && foodInventory()!=0 ) {
                    int randomInt = random2(1,10);
                    if (randomInt == 5)	{RandomMouseMove();}
                    return;}
            }
        }

        private boolean Equipped() {

            for (Item item : client.getItemContainer(InventoryID.EQUIPMENT).getItems())
            {
                if (item == null)
                {
                    continue;
                }

                if (item.getId() == ItemID.MITHRIL_SCIMITAR) {
                    return true;
                }
            }
            return false;
        }

        private Point BankLocation(int index) {
            // row = 1, n = index, column = n , while n>8 , column = n-8 row = row +1
            // row 1 y = 115  // row 2 y = 153  // column 1 x = 420 // column 2 x = 469  // 519  ,  189
            //canvas bank 355,88 	// column spacing of 50, tolerance 23 	// row spacing 37 , tolerance 22
            int bankBaseX = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getX();
            int bankBaseY = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER).getCanvasLocation().getY();
            int n = index;
            int relateBaseX= 75;
            int relateBaseY= 27;
            int columnFactor = 47;
            int rowFactor = 37;
            int row = 1;
            if (n>8){
                while (n>8){n =n-8; row = row + 1;}}
            int column = n;
            int x = bankBaseX + relateBaseX + (column-1)*columnFactor;
            int y = bankBaseY + relateBaseY + (row-1)*rowFactor;
            int xTolerance = x+random2(-8,8);
            int yTolerance = y+random2(-8,8);
            Point itemBankLocation = new Point (xTolerance,yTolerance);
            return itemBankLocation;
        }

        private Point InvLocation(int index) {

            int invBaseX = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getX();
            int invBaseY = client.getWidget(WidgetInfo.INVENTORY).getCanvasLocation().getY();
            int n = index;
            int relateBaseX= 14;
            int relateBaseY= 15;
            int columnFactor = 42;
            int rowFactor = 36;
            int row = 1;
            if (n>4){
                while (n>4){n =n-4; row = row + 1;}}
            int column = n;
            int x = invBaseX + relateBaseX + (column-1)*columnFactor;
            int y = invBaseY + relateBaseY + (row-1)*rowFactor;
            int xTolerance = x+random2(-10,10);
            int yTolerance = y+random2(-10,10);
            Point itemInvLocation = new Point (xTolerance,yTolerance);
            return itemInvLocation;
        }

        private static final Set<Integer> FOOD_IDS = ImmutableSet.of(ItemID.LOBSTER, ItemID.SHRIMPS);

        private int foodInventory() {
            int availableFood = 0;
            if (client.getItemContainer(InventoryID.INVENTORY)==null){return availableFood;}

            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                //System.out.print(" || items id " + item.getId());
                int itemId = item.getId();
                if (FOOD_IDS.contains(itemId)){	availableFood=availableFood+1;	}}
            return availableFood; 	}

        private void eatFood () throws AWTException {
            while(client.getWidget(WidgetInfo.INVENTORY).isHidden()){
                int inventoryIconTopLeftX = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().x;
                int inventoryIconTopLeftY = client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().y;
                int inventoryIconXWidth = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getWidth();
                int inventoryIconYHeight = (int) client.getWidget(WidgetInfo.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_TAB).getBounds().getHeight();
                int inventoryIconX = inventoryIconTopLeftX +3+ random2(0,inventoryIconXWidth-6);
                int inventoryIconY = inventoryIconTopLeftY+3 + random2(0,inventoryIconYHeight-6);
                Point inventoryIcon = new Point (inventoryIconX,inventoryIconY);
                Robot robot = new Robot();
                moveMouse(robot,client.getMouseCanvasPosition(),inventoryIcon,10,5,5);
                try {  Thread.sleep(random2(150,260));  } catch (InterruptedException e) { e.printStackTrace();}
                leftClick(robot);
                try {  Thread.sleep(random2(415,560));  } catch (InterruptedException e) { e.printStackTrace();}
            }
            int foodIndex = InvFoodIndex ();
            Point foodInvLocation = InvLocation(foodIndex);
            Robot robot = new Robot();
            moveMouse(robot,client.getMouseCanvasPosition(),foodInvLocation,10,5,5);
            try {  Thread.sleep(random2(150,260));  } catch (InterruptedException e) { e.printStackTrace();}
            leftClick(robot);
            try {  Thread.sleep(random2(415,560));  } catch (InterruptedException e) { e.printStackTrace();}
        }

        private int InvFoodIndex () {
            Item[] inventory = client.getItemContainer(InventoryID.INVENTORY).getItems();
            for (Item item : inventory) {
                int foodIndex = 1;
                int itemId = item.getId();
                if (FOOD_IDS.contains(itemId)) {
                    return foodIndex;
                }
                foodIndex = foodIndex + 1;
            }
            return 0;
        }
        private void bankWalk() throws AWTException {

            WorldPoint bankLocation = new WorldPoint(random2(3092,3097),random2(3240,3246),0);
            //System.out.print("random bank " + bankLocation);
            walk(bankLocation);
        }

        private void bankInvent() throws AWTException, InterruptedException {
            while(!checkMovementIdle()){Thread.sleep(1063);}
            WorldPoint bankBooth10355 = new WorldPoint(3091,3245,0);
            Point bankBoothPerspective = worldToCanvas(bankBooth10355);
            if(bankBoothPerspective.getX()>8 && bankBoothPerspective.getX()<1620 && bankBoothPerspective.getY()>180 && bankBoothPerspective.getY()<810){
                Robot robot = new Robot();
                moveMouse(robot,client.getMouseCanvasPosition(),bankBoothPerspective,11,4,4);
                rightClick(robot);
                //bank at x-45/+45,y+21/29,
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                Point selectBank = MenuIndexPosition(MenuIndex("Bank"),bankBoothPerspective);
                //Point selectBankPerspective = new Point(bankBoothPerspective.getX()+random2(-45,45),bankBoothPerspective.getY()+random2(24,29));
                moveMouse(robot,bankBoothPerspective,selectBank,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(1100,2400));  } catch (InterruptedException e) { e.printStackTrace(); }
                while(!checkMovementIdle()){Thread.sleep(408);}
                //BANK_DEPOSIT_INVENTORY = 1041; deposit:775-782,774-797
                //Point depositInventory = new Point(random2(890,909),random2(800,810));
                if(depositInventoryPoint()==null){return;}
                Point depositInventoryPoint1 = depositInventoryPoint();
                try {  Thread.sleep(random2(400,500));  } catch (InterruptedException e) { e.printStackTrace(); }
                moveMouse(robot,selectBank,depositInventoryPoint1,11,4,4);
                leftClick(robot);
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                //net 555-576,167-188
                Point axeBanklocation = BankLocation(getBankItemIndex(ItemID.MITHRIL_AXE));
                //System.out.println("Item net : " + " , "  );
                //Point withdrawNet = new Point(random2(676,699),random2(182,198));
                moveMouse1(robot,depositInventoryPoint1,axeBanklocation,11,4,4);
                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                leftClick(robot);
            }}

        private Point worldToCanvas(WorldPoint worldpoint){
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            Point perspective = Perspective.localToCanvas(client, targetLL, worldpoint.getPlane());
            Point adjustedPerspective = new Point(perspective.getX()+32, perspective.getY() - 18);
            return adjustedPerspective;}

        private Point worldToMiniMap(WorldPoint worldpoint) {
            LocalPoint targetLL = LocalPoint.fromWorld(client, worldpoint.getX(), worldpoint.getY());
            if (targetLL != null) {
                Point minimapPerspective = Perspective.localToMinimap(client, targetLL);
                if (minimapPerspective != null) {
                    Point adjustedMinimapPerspective = new Point(minimapPerspective.getX() +4, minimapPerspective.getY() + 23);
                    return adjustedMinimapPerspective;
                }}
            return null; }

        private Point depositInventoryPoint(){
            if(client.getWidget(12,42)==null){return null;}
            Widget deposit_Inventory_Widget = client.getWidget(12,42);
            int deposit_x = (int)Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinX()+4, deposit_Inventory_Widget.getBounds().getMaxX())-4);
            int deposit_y = (int)Math.round(random2Dbl(deposit_Inventory_Widget.getBounds().getMinY() +18, deposit_Inventory_Widget.getBounds().getMaxY())+11);
            return new Point (deposit_x, deposit_y );}

        private void walk(WorldPoint finalLocation) throws AWTException {
            Robot robot = new Robot();
            int walkX = 0;
            int walkY = 0;
            int walkPlane = 0;
            WorldPoint temporaryTarget = new WorldPoint(walkX, walkY, walkPlane);
            temporaryTarget = finalLocation;

            Point temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
            System.out.println("temporary target 1st " + temporaryTarget + " , " + temporaryTargetPerspective);
            //WorldPoint ShrimpFishArea = new WorldPoint(3087,3228,0);

            if (temporaryTargetPerspective != null) {
                if (temporaryTargetPerspective.getX() > 1500 && temporaryTargetPerspective.getX() < 1620 && temporaryTargetPerspective.getY() > 30
                        && temporaryTargetPerspective.getY() < 130) {

                    moveMouse(robot, client.getMouseCanvasPosition(), temporaryTargetPerspective, 11, 3, 3);
                    leftClick(robot);
                    return;
                }
            }

            int startX = client.getLocalPlayer().getWorldLocation().getX();
            int endX = temporaryTarget.getX();
            int midwayX = Math.abs(startX - endX) / 2 + Math.min(startX, endX);
            int startY = client.getLocalPlayer().getWorldLocation().getY();
            int endY = temporaryTarget.getY();
            int midwayY = Math.abs(startY - endY) / 2 + Math.min(startY, endY);
            temporaryTarget = new WorldPoint(midwayX, midwayY, client.getLocalPlayer().getWorldLocation().getPlane());
            temporaryTargetPerspective = worldToMiniMap(temporaryTarget);

            while (temporaryTargetPerspective.getX() <= 1500 && temporaryTargetPerspective.getX() >= 1620 && temporaryTargetPerspective.getY() <= 30 && temporaryTargetPerspective.getY() >= 130) {

                endX = temporaryTarget.getX();
                midwayX = Math.abs(startX - endX) / 2 + Math.min(startX, endX);
                endY = temporaryTarget.getY();
                midwayY = Math.abs(startY - endY) / 2 + Math.min(startY, endY);
                temporaryTarget = new WorldPoint(midwayX, midwayY, client.getLocalPlayer().getWorldLocation().getPlane());
                temporaryTargetPerspective = worldToMiniMap(temporaryTarget);
                //System.out.println("temporary target iter'" + temporaryTarget);
            }
            //System.out.println("temporary target iter used" + temporaryTarget);
            moveMouse(robot, client.getMouseCanvasPosition(), temporaryTargetPerspective, 11, 4, 4);
            leftClick(robot);
        }

        private final Set<Integer> RANDOM_IDS = ImmutableSet.of(NpcID.BEE_KEEPER, NpcID.BEE_KEEPER_6747, NpcID.CAPT_ARNAV, NpcID.NILES, NpcID.MILES, NpcID.GILES, NpcID.SERGEANT_DAMIEN,
                NpcID.DRUNKEN_DWARF, NpcID.FREAKY_FORESTER, NpcID.FROG,NpcID.GENIE,NpcID.EVIL_BOB,NpcID.POSTIE_PETE,NpcID.LEO,NpcID.DR_JEKYLL,NpcID.MYSTERIOUS_OLD_MAN,NpcID.MYSTERIOUS_OLD_MAN_6742,
                NpcID.MYSTERIOUS_OLD_MAN_6750, NpcID.MYSTERIOUS_OLD_MAN_6751, NpcID.MYSTERIOUS_OLD_MAN_6752, NpcID.MYSTERIOUS_OLD_MAN_6753, NpcID.FLIPPA,NpcID.FLIPPA_6744,
                NpcID.PILLORY_GUARD, NpcID.QUIZ_MASTER, NpcID.RICK_TURPENTINE,NpcID.SANDWICH_LADY,NpcID.SECURITY_GUARD,NpcID.STRANGE_PLANT,NpcID.DUNCE);

        private NPC randomNPCCheck (){
            List<NPC> activeRandom = new ArrayList<>();
            List<NPC> NPCList;
            if(client.getNpcs()==null){return null;}
            NPCList = client.getNpcs();
            for (NPC npc : NPCList)
            {	if (RANDOM_IDS.contains(npc.getId()))
            {	activeRandom.add(npc);	}}
            if(activeRandom.size()!=0) {
                for (NPC random : activeRandom){
                    if (random.getOverheadText()!=null){
                        if (random.getOverheadText().contains("Vein")){
                            NPC TargetRandom = random;
                            return random;
                        }}
                }}
            return null;
        }

        private void dismissRandom() throws AWTException {
            NPC targetRandom = randomNPCCheck();
            WorldPoint randomWL = targetRandom.getWorldLocation();
            Point randomCanvas = worldToCanvas(randomWL);
            if(randomCanvas.getX()>8 && randomCanvas.getX()<1620 && randomCanvas.getY()>180 && randomCanvas.getY()<810) {
                Robot robot = new Robot();
                moveMouse(robot,client.getMouseCanvasPosition(),randomCanvas,11,4,4);
                rightClick(robot);

                try {  Thread.sleep(random2(500,800));  } catch (InterruptedException e) { e.printStackTrace(); }
                Point selectDismiss = MenuIndexPosition(MenuIndex("Dismiss"),randomCanvas);

                moveMouse(robot,randomCanvas,selectDismiss,11,4,4);
                leftClick(robot);
            }
        }

        final String[] randomEventActions = new String[] { "Talk-to", "Dismiss", "Examine" };
        // final NPCComposition randomEventComp = activeRandom.get(0).getComposition();

        private int getBankItemIndex(int itemID) {
            int index = 1;
            Item[] bankItems = client.getItemContainer(InventoryID.BANK).getItems();
            //System.out.println(Arrays.toString(bankItems));
            for (Item item : bankItems) {
                if (item.getId() == itemID) {
                    return index;
                } else {index = index + 1;}
            }
            return index = 0;
        }

        private List<NPC> inverseSortTreeDistanceFromPlayer(List<NPC> localTarget)
        {

            if (localTarget.isEmpty())
            {
                return null;
            }

            final LocalPoint cameraPoint = new LocalPoint(client.getCameraX(), client.getCameraY());
            localTarget.sort(
                    Comparator.comparing(
                            // Negate to have the furthest first
                            (NPC npc) -> npc.getLocalLocation().distanceTo(cameraPoint))
                            // Order by position
                            .thenComparing(NPC::getLocalLocation, Comparator.comparing(LocalPoint::getX)
                                    .thenComparing(LocalPoint::getY))
                            // And then by id
                            .thenComparing(NPC::getId)
            );
            return localTarget;
        }

    }

}
