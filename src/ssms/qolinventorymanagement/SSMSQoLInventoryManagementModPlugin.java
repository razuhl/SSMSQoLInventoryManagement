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

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.campaign.CampaignEngine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import ssms.qol.properties.PropertiesContainer;
import ssms.qol.properties.PropertiesContainerConfiguration;
import ssms.qol.properties.PropertiesContainerConfigurationFactory;
import ssms.qol.properties.PropertiesContainerMerger;
import ssms.qol.properties.PropertyConfigurationBoolean;
import ssms.qol.properties.PropertyConfigurationContainer;
import ssms.qol.properties.PropertyConfigurationFloat;
import ssms.qol.properties.PropertyConfigurationInteger;
import ssms.qol.properties.PropertyConfigurationListContainer;
import ssms.qol.properties.PropertyConfigurationListSelectable;
import ssms.qol.properties.PropertyConfigurationSelectable;
import ssms.qol.properties.PropertyValueGetter;
import ssms.qol.properties.PropertyValueSetter;
import ssms.qol.util.UtilItems;

/**
 *
 * @author Malte Schulze
 */
public class SSMSQoLInventoryManagementModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() throws Exception {
        final Logger logger = Global.getLogger(SSMSQoLInventoryManagementModPlugin.class);
        logger.setLevel(Level.INFO);
    }
    
    @Override
    public void onGameLoad(boolean newGame) {
        final Logger logger = Global.getLogger(SSMSQoLInventoryManagementModPlugin.class);
        CampaignPlugin cp = null;
        List<com.fs.starfarer.api.campaign.CampaignPlugin> plugins = CampaignEngine.getInstance().getPlugins();
        for ( com.fs.starfarer.api.campaign.CampaignPlugin plugin : plugins ) {
            if ( plugin.getId().equals(CampaignPlugin.ID) ) {
                logger.log(Level.INFO, "plugin loaded");
                cp = (CampaignPlugin) plugin;
                break;
            }
        }
        if ( cp == null ) {
            cp = new CampaignPlugin();
            Global.getSector().registerPlugin(cp);
            logger.log(Level.INFO, "plugin created");
        }
        configure(cp);
    }
    
    protected void configure(final CampaignPlugin cp) {
        PropertiesContainerConfigurationFactory confFactory = PropertiesContainerConfigurationFactory.getInstance();
        PropertiesContainerConfiguration<WeaponStorage> confWS = confFactory.getOrCreatePropertiesContainerConfiguration("SSMSQoLInventoryManagementWeaponStorage", WeaponStorage.class);
        confWS.addProperty(new PropertyConfigurationListSelectable<WeaponStorage>("entityIds","Markets","A list of eligible markets(planets, spacestations, etc.) that will be used to exchange weapons if they meet all other criterias.",
                null,10,new PropertyValueGetter<WeaponStorage, List>() {
            @Override
            public List get(WeaponStorage sourceObject) {
                return sourceObject.entityIds;
            }
        }, new PropertyValueSetter<WeaponStorage, List>() {
            @Override
            public void set(WeaponStorage sourceObject, List value) {
                sourceObject.entityIds = value;
            }
        }, true, String.class) {
            private final Map<Object,String> labelCache = new HashMap<>();
            
            @Override
            public List buildOptions() {
                List<String> markets = new ArrayList<>();
                for ( StarSystemAPI ss : Global.getSector().getStarSystems() ) {
                    for ( SectorEntityToken token : ss.getAllEntities() ) {
                        if ( WeaponStorage.qualifiesAsStorage(token) ) {
                            markets.add(token.getId());
                        }
                    }
                }
                return markets;
            }

            @Override
            public String getOptionLabel(Object o) {
                String label = labelCache.get(o);
                if ( label == null ) {
                    outer : for ( StarSystemAPI ss : Global.getSector().getStarSystems() ) {
                        for ( SectorEntityToken token : ss.getAllEntities() ) {
                            if ( o.equals(token.getId()) ) {
                                label = token.getName();
                                labelCache.put(o, label);
                                break outer;
                            }
                        }
                    }
                }
                return label != null ? label : (String)o;
            }
        });
        confWS.addProperty(new PropertyConfigurationInteger<>("threshold","Threshold","Store up to this threshold of a single weapon, excess is transfered to the fleets cargo.",1000,20, 
                new PropertyValueGetter<WeaponStorage, Integer>() {
            @Override
            public Integer get(WeaponStorage sourceObject) {
                return sourceObject.threshold;
            }
        }, new PropertyValueSetter<WeaponStorage, Integer>() {
            @Override
            public void set(WeaponStorage sourceObject, Integer value) {
                sourceObject.threshold = value;
            }
        }, false, 0, Integer.MAX_VALUE));
        confWS.addProperty(new PropertyConfigurationBoolean<>("keepWeaponsWithoutBlueprint","Keep unknown Weapons","If true then weapons for which the blueprint is not known will be kept in storage even in excess of the threshold.",true,30, 
                new PropertyValueGetter<WeaponStorage, Boolean>() {
            @Override
            public Boolean get(WeaponStorage sourceObject) {
                return sourceObject.keepWeaponsWithoutBlueprint;
            }
        }, new PropertyValueSetter<WeaponStorage, Boolean>() {
            @Override
            public void set(WeaponStorage sourceObject, Boolean value) {
                sourceObject.keepWeaponsWithoutBlueprint = value;
            }
        }, false));
        confWS.configureMinorGameScoped(new PropertyValueGetter<PropertiesContainer<WeaponStorage>, String>() {
            @Override
            public String get(PropertiesContainer<WeaponStorage> sourceObject) {
                return "Weapon Storage";
            }
        });
        
        PropertiesContainerConfiguration<SafeStorage> confSS = confFactory.getOrCreatePropertiesContainerConfiguration("SSMSQoLInventoryManagementSafeStorage", SafeStorage.class);
        confSS.addProperty(new PropertyConfigurationListSelectable<SafeStorage>("entityIds","Markets","A list of eligible markets(planets, spacestations, etc.) that will be used to store items if they meet all other criterias.",
                null,10,new PropertyValueGetter<SafeStorage, List>() {
            @Override
            public List get(SafeStorage sourceObject) {
                return sourceObject.entityIds;
            }
        }, new PropertyValueSetter<SafeStorage, List>() {
            @Override
            public void set(SafeStorage sourceObject, List value) {
                sourceObject.entityIds = value;
            }
        }, true, String.class) {
            private final Map<Object,String> labelCache = new HashMap<>();
            
            @Override
            public List buildOptions() {
                List<String> markets = new ArrayList<>();
                for ( StarSystemAPI ss : Global.getSector().getStarSystems() ) {
                    for ( SectorEntityToken token : ss.getAllEntities() ) {
                        if ( SafeStorage.qualifiesAsStorage(token) ) {
                            markets.add(token.getId());
                        }
                    }
                }
                return markets;
            }

            @Override
            public String getOptionLabel(Object o) {
                String label = labelCache.get(o);
                if ( label == null ) {
                    outer : for ( StarSystemAPI ss : Global.getSector().getStarSystems() ) {
                        for ( SectorEntityToken token : ss.getAllEntities() ) {
                            if ( o.equals(token.getId()) ) {
                                label = token.getName();
                                labelCache.put(o, label);
                                break outer;
                            }
                        }
                    }
                }
                if ( label != null ) return label;
                else {
                    labelCache.put(o, (String)o);
                    return (String)o;
                }
            }
        });
        confSS.addProperty(new PropertyConfigurationListSelectable<SafeStorage>("itemsToStore","Items","A list of items that will be moved into safe storage.",
                null,10,new PropertyValueGetter<SafeStorage, List>() {
            @Override
            public List get(SafeStorage sourceObject) {
                return sourceObject.itemsToStore;
            }
        }, new PropertyValueSetter<SafeStorage, List>() {
            @Override
            public void set(SafeStorage sourceObject, List value) {
                sourceObject.itemsToStore = value;
            }
        }, true, String.class) {
            @Override
            public List buildOptions() {
                List<String> items = new ArrayList<>();
                for ( UtilItems.ItemId itemId : UtilItems.getInstance().getAllItemIds() ) {
                    items.add(itemId.uniqueId);
                }
                return items;
            }

            @Override
            public String getOptionLabel(Object o) {
                return UtilItems.getInstance().getItemIdForUniqueId((String)o).label;
            }
        });
        confSS.addProperty(new PropertyConfigurationBoolean<>("storeAllRecipes","Store Blueprints","If true then all blueprints will be stored.",true,30, 
                new PropertyValueGetter<SafeStorage, Boolean>() {
            @Override
            public Boolean get(SafeStorage sourceObject) {
                return sourceObject.storeAllRecipes;
            }
        }, new PropertyValueSetter<SafeStorage, Boolean>() {
            @Override
            public void set(SafeStorage sourceObject, Boolean value) {
                sourceObject.storeAllRecipes = value;
            }
        }, false));
        confSS.configureMinorGameScoped(new PropertyValueGetter<PropertiesContainer<SafeStorage>, String>() {
            @Override
            public String get(PropertiesContainer<SafeStorage> sourceObject) {
                return "Safe Storage";
            }
        });
        
        PropertiesContainerConfiguration<AutoTrade.ItemTradeRule> confITR = confFactory.getOrCreatePropertiesContainerConfiguration("SSMSQoLInventoryManagementItemTradeRule", AutoTrade.ItemTradeRule.class);
        confITR.addProperty(new PropertyConfigurationSelectable<AutoTrade.ItemTradeRule,String>("itemId","Item","Which item this rule applies to.",null,10,String.class,new PropertyValueGetter<AutoTrade.ItemTradeRule, String>() {
            @Override
            public String get(AutoTrade.ItemTradeRule sourceObject) {
                return sourceObject.itemId;
            }
        }, new PropertyValueSetter<AutoTrade.ItemTradeRule, String>() {
            @Override
            public void set(AutoTrade.ItemTradeRule sourceObject, String value) {
                sourceObject.itemId = value;
            }
        }, true) {
            @Override
            public List<String> buildOptions() {
                List<String> names = new ArrayList<>();
                for ( UtilItems.ItemId o : UtilItems.getInstance().getAllItemIds() ) names.add(o.uniqueId);
                return names;
            }

            @Override
            public String getOptionLabel(String o) {
                return UtilItems.getInstance().getItemIdForUniqueId((String)o).label;
            }
        });
        confITR.addProperty(new PropertyConfigurationInteger<>("demand","Demand","How much of an item the fleet should carry.",0,15, new PropertyValueGetter<AutoTrade.ItemTradeRule, Integer>() {
            @Override
            public Integer get(AutoTrade.ItemTradeRule sourceObject) {
                return sourceObject.demand;
            }
        }, new PropertyValueSetter<AutoTrade.ItemTradeRule, Integer>() {
            @Override
            public void set(AutoTrade.ItemTradeRule sourceObject, Integer value) {
                sourceObject.demand = value;
            }
        }, true,0,Integer.MAX_VALUE));
        confITR.addProperty(new PropertyConfigurationFloat<>("minSellPrice","Min Sell Price","Price must be at least this high to automatically sell excess.",0f,20, new PropertyValueGetter<AutoTrade.ItemTradeRule, Float>() {
            @Override
            public Float get(AutoTrade.ItemTradeRule sourceObject) {
                return sourceObject.minSellPrice;
            }
        }, new PropertyValueSetter<AutoTrade.ItemTradeRule, Float>() {
            @Override
            public void set(AutoTrade.ItemTradeRule sourceObject, Float value) {
                sourceObject.minSellPrice = value;
            }
        },false,0f,Float.MAX_VALUE));
        confITR.addProperty(new PropertyConfigurationFloat<>("maxBuyPrice","Max Buy Price","Price may not be higher than this to buy demand.",1000f,30, new PropertyValueGetter<AutoTrade.ItemTradeRule, Float>() {
            @Override
            public Float get(AutoTrade.ItemTradeRule sourceObject) {
                return sourceObject.maxBuyPrice;
            }
        }, new PropertyValueSetter<AutoTrade.ItemTradeRule, Float>() {
            @Override
            public void set(AutoTrade.ItemTradeRule sourceObject, Float value) {
                sourceObject.maxBuyPrice = value;
            }
        },false,0f,Float.MAX_VALUE));
        confITR.configureMinorGameScoped(new PropertyValueGetter<PropertiesContainer<AutoTrade.ItemTradeRule>, String>() {
            @Override
            public String get(PropertiesContainer<AutoTrade.ItemTradeRule> pc) {
                return new StringBuilder(UtilItems.getInstance().getItemIdForUniqueId(pc.getFieldValue("itemId", String.class)).label).append(", =").append(pc.getFieldValue("demand", Integer.class))
                        .append(" >").append(pc.getFieldValue("minSellPrice", Float.class)).append(" <").append(pc.getFieldValue("maxBuyPrice", Float.class)).toString();
            }
        });
        
        PropertiesContainerConfiguration<AutoTrade> confAT = confFactory.getOrCreatePropertiesContainerConfiguration("SSMSQoLInventoryManagementAutoTrade", AutoTrade.class);
        confAT.addProperty(new PropertyConfigurationListContainer<>("rules","Rules","A list of rules that define how items should be traded.",null,10,
            new PropertyValueGetter<AutoTrade, List>() {
                @Override
                public List get(AutoTrade sourceObject) {
                    return sourceObject.rules;
                }
            },new PropertyValueSetter<AutoTrade, List>() {
                @Override
                public void set(AutoTrade sourceObject, List value) {
                    sourceObject.rules = value;
                }
            }, true, "SSMSQoLInventoryManagementItemTradeRule", true, true, new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                return new AutoTrade.ItemTradeRule();
            }
        }));
        confAT.addProperty(new PropertyConfigurationBoolean<>("sellKnownWeapons","Sell known weapons","If true then all known weapons will be sold.",false,20, 
                new PropertyValueGetter<AutoTrade, Boolean>() {
            @Override
            public Boolean get(AutoTrade sourceObject) {
                return sourceObject.sellKnownWeapons;
            }
        }, new PropertyValueSetter<AutoTrade, Boolean>() {
            @Override
            public void set(AutoTrade sourceObject, Boolean value) {
                sourceObject.sellKnownWeapons = value;
            }
        }, false));
        confAT.configureMinorGameScoped(new PropertyValueGetter<PropertiesContainer<AutoTrade>, String>() {
            @Override
            public String get(PropertiesContainer<AutoTrade> pc) {
                return "Auto Trade";
            }
        });
        
        PropertiesContainerConfiguration<CampaignPlugin> confCP = confFactory.getOrCreatePropertiesContainerConfiguration("SSMSQoLInventoryManagement", CampaignPlugin.class);
        confCP.addProperty(new PropertyConfigurationContainer<>("weaponStorage","Weapon Storage","Behaviour for exchanging weapons with a storage on a market.",new WeaponStorage(),"SSMSQoLInventoryManagementWeaponStorage",WeaponStorage.class,10,new PropertyValueGetter<CampaignPlugin, WeaponStorage>() {
                @Override
                public WeaponStorage get(CampaignPlugin sourceObject) {
                    return sourceObject.weaponStorage;
                }
            }, new PropertyValueSetter<CampaignPlugin, PropertiesContainer>() {
                @Override
                public void set(CampaignPlugin sourceObject, PropertiesContainer value) {
                    if ( sourceObject.weaponStorage == null && value != null ) {
                        WeaponStorage ws = new WeaponStorage();
                        ws.entityIds = (List)value.getFieldValue("entityIds", List.class);
                        ws.threshold = (Integer)value.getFieldValue("threshold", Integer.class);
                        ws.keepWeaponsWithoutBlueprint = (Boolean)value.getFieldValue("keepWeaponsWithoutBlueprint", Boolean.class);
                        sourceObject.weaponStorage = ws;
                    }
                }
            }, false));
        confCP.addProperty(new PropertyConfigurationContainer<>("safeStorage","Safe Storage","Behaviour for storing items on a safe market.",new SafeStorage(),"SSMSQoLInventoryManagementSafeStorage",SafeStorage.class,20,new PropertyValueGetter<CampaignPlugin, SafeStorage>() {
                @Override
                public SafeStorage get(CampaignPlugin sourceObject) {
                    return sourceObject.safeStorage;
                }
            }, new PropertyValueSetter<CampaignPlugin, PropertiesContainer>() {
                @Override
                public void set(CampaignPlugin sourceObject, PropertiesContainer value) {
                    if ( sourceObject.safeStorage == null && value != null ) {
                        SafeStorage st = new SafeStorage();
                        st.entityIds = (List)value.getFieldValue("entityIds", List.class);
                        st.itemsToStore = (List)value.getFieldValue("itemsToStore", List.class);
                        st.storeAllRecipes = (Boolean)value.getFieldValue("storeAllRecipes", Boolean.class);
                        sourceObject.safeStorage = st;
                    }
                }
            }, false));
        confCP.addProperty(new PropertyConfigurationContainer<>("autoTrade","Auto Trade","Behaviour for selling items on a market.",new AutoTrade(),"SSMSQoLInventoryManagementAutoTrade",AutoTrade.class,30,new PropertyValueGetter<CampaignPlugin, AutoTrade>() {
                @Override
                public AutoTrade get(CampaignPlugin sourceObject) {
                    return sourceObject.autoTrade;
                }
            }, new PropertyValueSetter<CampaignPlugin, PropertiesContainer>() {
                @Override
                public void set(CampaignPlugin sourceObject, PropertiesContainer value) {
                    if ( sourceObject.autoTrade == null && value != null ) {
                        AutoTrade at = new AutoTrade();
                        at.rules = (List)value.getFieldValue("rules", List.class);
                        at.sellKnownWeapons = (Boolean)value.getFieldValue("sellKnownWeapons", Boolean.class);
                        sourceObject.autoTrade = at;
                    }
                }
            }, false));
        confCP.addProperty(new PropertyConfigurationBoolean<>("uninstall","Uninstall","After activating this you have to save the game. Restart without the mod active and you can load the savegame.",Boolean.FALSE,40,new PropertyValueGetter<CampaignPlugin, Boolean>() {
            @Override
            public Boolean get(CampaignPlugin sourceObject) {
                return Boolean.FALSE;
            }
        }, null, false));
        confCP.addSetter(new PropertiesContainerMerger<CampaignPlugin>() {
            @Override
            public boolean merge(PropertiesContainer<CampaignPlugin> container, CampaignPlugin sourceObject) {
                Boolean uninstall = container.getFieldValue("uninstall", Boolean.class);
                if ( uninstall != null && uninstall ) {
                    Iterator<com.fs.starfarer.api.campaign.CampaignPlugin> plugins = CampaignEngine.getInstance().getPlugins().iterator();
                    while ( plugins.hasNext() ) {
                        if ( plugins.next().getId().equals(CampaignPlugin.ID) ) {
                            plugins.remove();
                        }
                    }
                }
                return true;
            }
        });
        confCP.configureGameScopedSingleInstance("Inventory Management", cp);
    }
}
