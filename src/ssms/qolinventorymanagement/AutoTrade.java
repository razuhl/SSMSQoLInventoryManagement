/*
 * Copyright (C) 2020 Malte Schulze.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library;  If not, see 
 * <https://www.gnu.org/licenses/>.
 */
package ssms.qolinventorymanagement;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SubmarketPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.campaign.fleet.CargoData;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Level;
import ssms.qol.util.UtilItems;
import ssms.qol.util.UtilItems.ItemId;
import ssms.qol.util.UtilTrade;

/**
 *
 * @author Malte Schulze
 */
public class AutoTrade implements Serializable {
    private static final long serialVersionUID = 8084276252099935477L;
    //private final int version = 1;
    
    static public class ItemTradeRule implements Serializable {
        private static final long serialVersionUID = -402053265894494692L;
        //private final int version = 1;
        
        public String itemId;
        public int demand;
        public float minSellPrice, maxBuyPrice;
        
        /*private void writeObject(java.io.ObjectOutputStream out) throws IOException {
            out.writeInt(version);
            out.writeObject(itemId);
            out.writeInt(demand);
            out.writeFloat(minSellPrice);
            out.writeFloat(maxBuyPrice);
        }

        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
            int storedVersion = in.readInt();
            itemId = (String) in.readObject();
            demand = in.readInt();
            minSellPrice = in.readFloat();
            maxBuyPrice = in.readFloat();
        }

        private void readObjectNoData() throws ObjectStreamException {
            itemId = null;
            demand = 0;
            minSellPrice = 1000000;
            maxBuyPrice = 0;
        }*/
    }
    protected class AvailableGoods implements Comparable<AvailableGoods> {
        public CargoAPI.CargoItemType type;
        public Object itemData;
        public float quantity;
        public float price;
        public PlayerMarketTransaction transaction;

        public AvailableGoods(float quantity, float price, PlayerMarketTransaction transaction, CargoAPI.CargoItemType type, Object itemData) {
            this.quantity = quantity;
            this.price = price;
            this.transaction = transaction;
            this.type = type;
            this.itemData = itemData;
        }

        @Override
        public int compareTo(AvailableGoods o) {
            return Float.compare(price, o.price);
        }
    }
    protected List<ItemTradeRule> rules;
    protected boolean sellKnownWeapons;
    
    public void trade(SectorEntityToken interactionTarget, CampaignUIAPI.CoreUITradeMode tradeMode) {
        UtilItems items = UtilItems.getInstance();
        UtilTrade trade = UtilTrade.getInstance();
        MarketAPI market = interactionTarget.getMarket();
        SubmarketAPI submarketPrimary = null, submarketSecondary = null, stockpile = market.getSubmarket("local_resources"), storage = market.getSubmarket("storage");
        
        if ( tradeMode == CampaignUIAPI.CoreUITradeMode.OPEN ) {
            submarketPrimary = market.getSubmarket("open_market");
            submarketSecondary = market.getSubmarket("generic_military");
        } else if ( tradeMode == CampaignUIAPI.CoreUITradeMode.SNEAK ) {
            submarketPrimary = market.getSubmarket("black_market");
            submarketSecondary = null;
        }
        if ( submarketPrimary != null ) submarketPrimary.getPlugin().updateCargoPrePlayerInteraction();
        if ( submarketSecondary != null ) submarketSecondary.getPlugin().updateCargoPrePlayerInteraction();
        if ( stockpile != null ) stockpile.getPlugin().updateCargoPrePlayerInteraction();
        if ( storage != null ) storage.getPlugin().updateCargoPrePlayerInteraction();
        CargoAPI fleetCargo = Global.getSector().getPlayerFleet().getCargo();
        
        if ( sellKnownWeapons ) {
            PlayerMarketTransaction transactionPrimary = submarketPrimary != null ? new PlayerMarketTransaction(submarketPrimary.getMarket(), submarketPrimary, tradeMode) : null;
            PlayerMarketTransaction transactionSecondary = submarketSecondary != null ? new PlayerMarketTransaction(submarketSecondary.getMarket(), submarketSecondary, tradeMode) : null;
            if ( transactionPrimary != null || transactionSecondary != null ) {
                List<CargoStackAPI> stacks = fleetCargo.getStacksCopy();
                FactionAPI pf = Global.getSector().getPlayerFaction();
                for ( CargoStackAPI stack : stacks ) {
                    ItemId itemId = items.getItemId(stack);
                    if ( stack.isWeaponStack() && stack.getSize() > 0 && pf.knowsWeapon(itemId.id) ) {
                        int countToSell = (int)stack.getSize();
                        //do not sell weapons below their demand
                        if ( rules != null ) {
                            for ( ItemTradeRule rule : rules ) {
                                if ( rule.itemId != null && rule.itemId.equals(itemId.uniqueId) ) {
                                    countToSell = Math.max(countToSell-rule.demand, 0);
                                    break;
                                }
                            }
                        }
                        if ( transactionPrimary != null && transactionPrimary.getSubmarket().isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_SELL) ) 
                            transactionPrimary.getSold().addWeapons(itemId.id, countToSell);
                        else if ( transactionSecondary != null && transactionSecondary.getSubmarket().isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_SELL) ) 
                            transactionSecondary.getSold().addWeapons(itemId.id, countToSell);
                    }
                }
                trade.doTransaction(transactionPrimary,fleetCargo);
                trade.doTransaction(transactionSecondary,fleetCargo);
            }
        }
        
        if ( rules != null ) {
            PlayerMarketTransaction transactionPrimary = submarketPrimary != null ? new PlayerMarketTransaction(submarketPrimary.getMarket(), submarketPrimary, tradeMode) : null;
            PlayerMarketTransaction transactionSecondary = submarketSecondary != null ? new PlayerMarketTransaction(submarketSecondary.getMarket(), submarketSecondary, tradeMode) : null;
            PlayerMarketTransaction transactionStockpile = stockpile != null ? new PlayerMarketTransaction(stockpile.getMarket(), stockpile, tradeMode) : null;
            PlayerMarketTransaction transactionStorage = storage != null ? new PlayerMarketTransaction(storage.getMarket(), storage, tradeMode) : null;
            
            Map<String,List<AvailableGoods>> goods = new HashMap<>();
            if ( stockpile != null && transactionStockpile != null ) {
                LocalResourcesSubmarketPlugin plugin = ((LocalResourcesSubmarketPlugin)stockpile.getPlugin());
                List<CargoStackAPI> stacks = stockpile.getCargo().getStacksCopy();
                //free stuff at the stockpile
                for ( CargoStackAPI stack : plugin.getLeft().getStacksCopy() ) {
                    if ( stockpile.isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_BUY) ) continue;
                    addToGoods(goods,items.getItemId(stack).uniqueId,stack.getSize(),0f,transactionStockpile,stack.getType(),stack.getData());
                }
                for ( CargoStackAPI stack : stacks ) {
                    if ( stockpile.isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_BUY) ) continue;
                    if ( stack.isCommodityStack() )
                        addToGoods(goods,items.getItemId(stack).uniqueId,stack.getSize(),LocalResourcesSubmarketPlugin.getStockpilingUnitPrice(stack.getResourceIfResource(), false),
                                transactionStockpile,stack.getType(),stack.getData());
                    else addToGoods(goods,items.getItemId(stack).uniqueId,stack.getSize(),stack.getBaseValuePerUnit(),transactionStockpile,stack.getType(),stack.getData());
                }
            }
            if ( storage != null && transactionStorage != null ) {
                List<CargoStackAPI> stacks = storage.getCargo().getStacksCopy();
                for ( CargoStackAPI stack : stacks ) {
                    if ( storage.isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_BUY) ) continue;
                    addToGoods(goods,items.getItemId(stack).uniqueId,stack.getSize(),0f,transactionStorage,stack.getType(),stack.getData());
                }
            }
            if ( submarketPrimary != null && transactionPrimary != null ) {
                List<CargoStackAPI> stacks = submarketPrimary.getCargo().getStacksCopy();
                for ( CargoStackAPI stack : stacks ) {
                    if ( submarketPrimary.isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_BUY) ) continue;
                    addToGoods(goods,items.getItemId(stack).uniqueId,stack.getSize(),trade.priceForBuying(stack,market)/stack.getSize(),transactionPrimary,stack.getType(),stack.getData());
                }
            }
            if ( submarketSecondary != null && transactionSecondary != null ) {
                List<CargoStackAPI> stacks = submarketSecondary.getCargo().getStacksCopy();
                for ( CargoStackAPI stack : stacks ) {
                    if ( submarketSecondary.isIllegalOnSubmarket(stack, SubmarketPlugin.TransferAction.PLAYER_BUY) ) continue;
                    addToGoods(goods,items.getItemId(stack).uniqueId,stack.getSize(),trade.priceForBuying(stack,market)/stack.getSize(),transactionSecondary,stack.getType(),stack.getData());
                }
            }
            
            for ( ItemTradeRule rule : rules ) {
                String itemId = rule.itemId;
                if ( itemId == null ) continue;
                List<CargoStackAPI> stacks = fleetCargo.getStacksCopy();
                CargoStackAPI cargoStack = null;
                for ( CargoStackAPI stack : stacks ) {
                    ItemId stackId = items.getItemId(stack);
                    if ( itemId.equals(stackId.uniqueId) ) {
                        cargoStack = stack;
                        break;
                    }
                }
                float quantity = cargoStack != null ? cargoStack.getSize() : 0;
                if ( rule.demand > quantity ) {
                    List<AvailableGoods> available = goods.get(itemId);
                    if ( available == null ) continue;
                    Collections.sort(available);
                    float outstandingDemand = rule.demand - quantity;
                    for ( AvailableGoods good : available ) {
                        if ( good.price > rule.maxBuyPrice ) break;
                        float quantityToBuy = Math.min(outstandingDemand, good.quantity);
                        if ( good.price == 0 ) {
                            //free items are transferred directly
                            CargoData cargo = new CargoData(false);
                            cargo.addItems(good.type, good.itemData, quantityToBuy);
                            fleetCargo.addAll(cargo);
                            good.transaction.getSubmarket().getCargo().removeAll(cargo);
                            Global.getLogger(SSMSQoLInventoryManagementModPlugin.class).log(Level.INFO, "Items were free: "+good.itemData+" "+quantityToBuy);
                        } else {
                            good.transaction.getBought().addItems(good.type, good.itemData, quantityToBuy);
                        }
                        outstandingDemand -= quantityToBuy;
                        if ( outstandingDemand <= 0f ) break;
                    }
                } else if ( rule.demand < quantity && cargoStack != null ) {
                    if ( transactionPrimary != null && !transactionPrimary.getSubmarket().isIllegalOnSubmarket(cargoStack, SubmarketPlugin.TransferAction.PLAYER_SELL) ) 
                        transactionPrimary.getSold().addItems(cargoStack.getType(), cargoStack.getData(), quantity-rule.demand);
                    else if ( transactionSecondary != null && !transactionSecondary.getSubmarket().isIllegalOnSubmarket(cargoStack, SubmarketPlugin.TransferAction.PLAYER_SELL) ) 
                        transactionSecondary.getSold().addItems(cargoStack.getType(), cargoStack.getData(), quantity-rule.demand);
                    else if ( transactionStorage != null && !transactionStorage.getSubmarket().isIllegalOnSubmarket(cargoStack, SubmarketPlugin.TransferAction.PLAYER_SELL) ) 
                        transactionStorage.getSold().addItems(cargoStack.getType(), cargoStack.getData(), quantity-rule.demand);
                }
            }
            trade.doTransaction(transactionStockpile,fleetCargo);
            trade.doTransaction(transactionStorage,fleetCargo);
            trade.doTransaction(transactionPrimary,fleetCargo);
            trade.doTransaction(transactionSecondary,fleetCargo);
            int creditsTraded = Math.round(
                    (transactionStockpile != null ? transactionStockpile.getCreditValue() : 0) + 
                    (transactionPrimary != null ? transactionPrimary.getCreditValue() : 0) + 
                    (transactionSecondary != null ? transactionSecondary.getCreditValue() : 0) + 
                    (transactionStorage != null ? transactionStorage.getCreditValue() : 0)
            );
            if ( creditsTraded < 0 ) Global.getSector().getCampaignUI().addMessage("Spent "+Math.abs(creditsTraded)+" credits through autotrade.");
            else if ( creditsTraded > 0 ) Global.getSector().getCampaignUI().addMessage("Gained "+creditsTraded+" credits through autotrade.");
        }
    }
    
    private void addToGoods(Map<String, List<AvailableGoods>> goods, String commodityId, float quantity, float price, PlayerMarketTransaction transaction, CargoAPI.CargoItemType type, Object itemData) {
        List<AvailableGoods> lst = goods.get(commodityId);
        if ( lst == null ) {
            lst = new ArrayList<>();
            goods.put(commodityId, lst);
        }
        lst.add(new AvailableGoods(quantity, price, transaction, type, itemData));
    }
    
    /*private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        out.writeObject(rules);
        out.writeBoolean(sellKnownWeapons);
    }
    
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        int storedVersion = in.readInt();
        rules = (List<ItemTradeRule>) in.readObject();
        sellKnownWeapons = in.readBoolean();
    }
    
    private void readObjectNoData() throws ObjectStreamException {
        rules = null;
        sellKnownWeapons = false;
    }*/
}
