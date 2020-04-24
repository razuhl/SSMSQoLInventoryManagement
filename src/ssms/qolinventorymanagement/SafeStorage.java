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
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import java.io.Serializable;
import java.util.List;
import ssms.qol.util.UtilItems;

/**
 *
 * @author Malte Schulze
 */
public class SafeStorage implements Serializable {
    private static final long serialVersionUID = -526977047742778414L;
    //private final int version = 1;
    
    protected List<String> entityIds;
    protected List<String> itemsToStore;
    protected boolean storeAllRecipes = true;
    
    public static boolean qualifiesAsStorage(SectorEntityToken interactionTarget) {
        return (interactionTarget.getFaction() == null || interactionTarget.getFaction().isPlayerFaction()) &&
                interactionTarget.getMarket() != null && interactionTarget.getMarket().getSubmarket("storage") != null;
    }
    
    public boolean isStorage(SectorEntityToken interactionTarget) {
        return interactionTarget != null && entityIds != null && entityIds.contains(interactionTarget.getId()) && 
                (interactionTarget.getFaction() == null || interactionTarget.getFaction().isPlayerFaction()) &&
                interactionTarget.getMarket() != null && interactionTarget.getMarket().getSubmarket("storage") != null;
    }

    public void exchangeItems(SectorEntityToken interactionTarget) {
        if ( !isStorage(interactionTarget) ) return;
        UtilItems items = UtilItems.getInstance();
        CargoAPI fleetCargo = Global.getSector().getPlayerFleet().getCargo();
        MarketAPI market = interactionTarget.getMarket();
        SubmarketAPI storage = market.getSubmarket("storage");
        CargoAPI storageCargo = storage.getCargo();
        
        storage.getPlugin().updateCargoPrePlayerInteraction();
        List<CargoStackAPI> itemsFromFleet = fleetCargo.getStacksCopy();
        StringBuilder storedItems = new StringBuilder();
        for ( CargoStackAPI item : itemsFromFleet ) {
            SpecialItemSpecAPI special = item.getSpecialItemSpecIfSpecial();
            String itemId = items.getItemId(item).uniqueId;
            if ( ( itemsToStore != null && itemsToStore.contains(itemId) ) || 
                    ( storeAllRecipes && special != null && ( special.hasTag("package_bp") || special.hasTag("single_bp") ) ) ) {
                float amount = item.getSize();
                storageCargo.addItems(item.getType(), item.getData(), amount);
                fleetCargo.removeItems(item.getType(), item.getData(), amount);
                storedItems.append(item.getDisplayName()).append(" x ").append(amount).append("\n");
            }
        }
        if ( storedItems.length() > 0 ) {
            Global.getSector().getCampaignUI().addMessage("Placed items in safe storage:\n"+storedItems.toString());
        }
    }
    
    /*private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        out.writeObject(entityIds);
        out.writeObject(itemsToStore);
        out.writeBoolean(storeAllRecipes);
    }
    
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        int storedVersion = in.readInt();
        entityIds = (List<String>) in.readObject();
        itemsToStore = (List<String>) in.readObject();
        storeAllRecipes = in.readBoolean();
    }
    
    private void readObjectNoData() throws ObjectStreamException {
        entityIds = null;
        itemsToStore = null;
        storeAllRecipes = false;
    }*/
}
