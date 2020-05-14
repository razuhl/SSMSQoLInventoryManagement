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
import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.IsSoughtByPatrols;
import com.fs.starfarer.api.util.Misc;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * 
 * @author Malte Schulze
 */
public class CampaignPlugin extends BaseCampaignPlugin implements Serializable {
    public static final String ID = "ssms.qolinventorymanagement.CampaignPlugin";
    private static final long serialVersionUID = -584521336928726861L;
    
    protected WeaponStorage weaponStorage = new WeaponStorage();
    protected SafeStorage safeStorage = new SafeStorage();
    protected AutoTrade autoTrade = new AutoTrade();

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        final Logger logger = Global.getLogger(SSMSQoLInventoryManagementModPlugin.class);
        
        if ( interactionTarget != null && interactionTarget.getMarket() != null ) {
            MarketAPI market = interactionTarget.getMarket();
            FactionAPI faction = interactionTarget.getFaction();
            //Make sure we can trade at the market
            CoreUITradeMode tm = CoreUITradeMode.NONE;
            if ( market.isPlayerOwned() ) tm = CoreUITradeMode.OPEN;
            else {
                if ( faction != null ) {
                    if ( !faction.isHostileTo(Global.getSector().getPlayerFaction()) ) {
                        if ( Global.getSector().getPlayerFleet().isTransponderOn() || market.isFreePort() || faction.getCustomBoolean("allowsTransponderOffTrade") ) {
                            tm = CoreUITradeMode.OPEN;
                        } else if ( !IsSoughtByPatrols(faction) ) {
                            tm = CoreUITradeMode.SNEAK;
                        }
                    }
                }
            }
            
            logger.log(Level.DEBUG, "Trademode: "+tm);
            if ( tm == CoreUITradeMode.NONE ) return null;
            
            if ( weaponStorage != null ) {
                weaponStorage.exchangeWeapons(interactionTarget);
            }
            if ( safeStorage != null ) {
                safeStorage.exchangeItems(interactionTarget);
            }
            if ( autoTrade != null ) {
                autoTrade.trade(interactionTarget, tm);
            }
        } else {
            logger.log(Level.DEBUG, "not a market");
        }
        return null;
    }
    
    protected boolean IsSoughtByPatrols(FactionAPI faction) {
        IsSoughtByPatrols rule = new IsSoughtByPatrols();
        List<Misc.Token> params = new ArrayList<>();
        params.add(new Misc.Token(faction.getId(), Misc.TokenType.LITERAL));
        return rule.execute(null, null, params, null);
    }
    
    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isTransient() {
        return false;
    }
    
    /*private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeInt(version);
        out.writeObject(weaponStorage);
        out.writeObject(safeStorage);
        out.writeObject(autoTrade);
    }
    
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        int storedVersion = in.readInt();
        weaponStorage = (WeaponStorage) in.readObject();
        safeStorage = (SafeStorage) in.readObject();
        autoTrade = (AutoTrade) in.readObject();
    }
    
    private void readObjectNoData() throws ObjectStreamException {
        //In case this gets added to an already serialized object as a superclass we need to initialize its values.
        weaponStorage = new WeaponStorage();
        safeStorage = new SafeStorage();
        autoTrade = new AutoTrade();
    }*/
}
