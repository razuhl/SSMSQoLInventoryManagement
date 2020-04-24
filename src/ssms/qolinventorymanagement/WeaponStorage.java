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
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;

/**
 *
 * @author Malte Schulze
 */
public class WeaponStorage implements Serializable {
    private static final long serialVersionUID = 3557997470155908244L;
    //private final int version = 1;
    
    protected List<String> entityIds;
    protected int threshold = 1000;
    protected boolean keepWeaponsWithoutBlueprint = true;
    
    public static boolean qualifiesAsStorage(SectorEntityToken interactionTarget) {
        return (interactionTarget.getFaction() == null || interactionTarget.getFaction().isPlayerFaction()) &&
                interactionTarget.getMarket() != null && interactionTarget.getMarket().getSubmarket("storage") != null;
    }

    public boolean isStorage(SectorEntityToken interactionTarget) {
        return interactionTarget != null && entityIds != null && entityIds.contains(interactionTarget.getId()) && 
                qualifiesAsStorage(interactionTarget);
    }

    public void exchangeWeapons(SectorEntityToken interactionTarget) {
        if ( !isStorage(interactionTarget) ) return;
        CargoAPI fleetCargo = Global.getSector().getPlayerFleet().getCargo();
        MarketAPI market = interactionTarget.getMarket();
        SubmarketAPI storage = market.getSubmarket("storage");
        CargoAPI storageCargo = storage.getCargo();
        
        storage.getPlugin().updateCargoPrePlayerInteraction();
        List<CargoAPI.CargoItemQuantity<String>> weapons = new ArrayList<>(fleetCargo.getWeapons());
        for ( CargoAPI.CargoItemQuantity<String> weapon : weapons ) {
            if ( weapon.getCount() > 0 ) {
                storageCargo.addWeapons(weapon.getItem(), weapon.getCount());
                fleetCargo.removeWeapons(weapon.getItem(), weapon.getCount());
            }
        }
        weapons = new ArrayList<>(storageCargo.getWeapons());
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        for ( CargoAPI.CargoItemQuantity<String> weapon : weapons ) {
            if ( weapon.getCount() > threshold && (!keepWeaponsWithoutBlueprint || playerFaction.knowsWeapon(weapon.getItem())) ) {
                int transfer = weapon.getCount() - threshold;
                fleetCargo.addWeapons(weapon.getItem(), transfer);
                storageCargo.removeWeapons(weapon.getItem(), transfer);
            }
        }
    }
    
    /*private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        out.writeObject(entityIds);
        out.writeInt(threshold);
        out.writeBoolean(keepWeaponsWithoutBlueprint);
    }
    
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        int storedVersion = in.readInt();
        entityIds = (List<String>) in.readObject();
        threshold = in.readInt();
        keepWeaponsWithoutBlueprint = in.readBoolean();
    }
    
    private void readObjectNoData() throws ObjectStreamException {
        entityIds = null;
        threshold = 1000;
        keepWeaponsWithoutBlueprint = false;
    }*/
}
