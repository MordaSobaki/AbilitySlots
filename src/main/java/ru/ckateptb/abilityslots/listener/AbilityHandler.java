/*
 * Copyright (c) 2022 CKATEPTb <https://github.com/CKATEPTb>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.ckateptb.abilityslots.listener;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import ru.ckateptb.abilityslots.ability.Ability;
import ru.ckateptb.abilityslots.ability.enums.ActivateResult;
import ru.ckateptb.abilityslots.ability.enums.ActivationMethod;
import ru.ckateptb.abilityslots.ability.enums.SequenceAction;
import ru.ckateptb.abilityslots.ability.info.AbilityInformation;
import ru.ckateptb.abilityslots.service.AbilityInstanceService;
import ru.ckateptb.abilityslots.service.AbilitySequenceService;
import ru.ckateptb.abilityslots.service.AbilityService;
import ru.ckateptb.abilityslots.service.AbilityUserService;
import ru.ckateptb.abilityslots.user.AbilityUser;
import ru.ckateptb.abilityslots.user.PlayerAbilityUser;
import ru.ckateptb.tablecloth.collision.collider.RayCollider;
import ru.ckateptb.tablecloth.ioc.annotation.Autowired;
import ru.ckateptb.tablecloth.ioc.annotation.Component;

import java.util.*;

@Component
public final class AbilityHandler implements Listener {
    private final AbilityUserService userService;
    private final AbilityService abilityService;
    private final AbilityInstanceService abilityInstanceService;
    private final AbilitySequenceService abilitySequenceService;

    public AbilityHandler(AbilityUserService userService,
                          AbilityService abilityService,
                          AbilityInstanceService abilityInstanceService,
                          AbilitySequenceService abilitySequenceService) {
        this.userService = userService;
        this.abilityService = abilityService;
        this.abilityInstanceService = abilityInstanceService;
        this.abilitySequenceService = abilitySequenceService;
    }

    public void activateAbility(AbilityUser abilityUser, AbilityInformation ability, ActivationMethod method) {
        if (!(abilityUser instanceof PlayerAbilityUser user)
                || ability == null
                || !ability.isActivatedBy(method)
                || !user.canActivate(ability)
                || user.getEntity().hasMetadata("tablecloth:paralyze")) return;
        Ability instance = ability.createAbility();
        instance.setUser(user);
        ActivateResult activateResult = instance.activate(method);
        if (isActivate(activateResult)) {
            abilityInstanceService.registerInstance(user, instance);
        }
    }

    public List<AbilityInformation> getHandledAbilities(AbilityUser user) {
        List<AbilityInformation> list = new ArrayList<>(abilityService.getPassiveAbilities());
        list.removeIf(passive -> !abilityInstanceService.hasAbility(user, passive));
        AbilityInformation selectedAbility = user.getSelectedAbility();
        list.add(selectedAbility);
        return list;
    }

    public void handleAbilities(AbilityUser user, ActivationMethod method) {
        this.getHandledAbilities(user).forEach(ability -> activateAbility(user, ability, method));
    }

    @EventHandler
    public void on(PlayerToggleSneakEvent event) {
        AbilityUser user = userService.getAbilityPlayer(event.getPlayer());
        if (user == null) return;

        boolean sneaking = event.isSneaking();
        if (isActivate(abilitySequenceService.registerAction(user, sneaking ? SequenceAction.SNEAK : SequenceAction.SNEAK_RELEASE))) {
            return;
        }
        handleAbilities(user, sneaking ? ActivationMethod.SNEAK : ActivationMethod.SNEAK_RELEASE);
    }

    @EventHandler
    public void on(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;
        AbilityUser user = userService.getAbilityUser(livingEntity);
        if (user == null) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            handleAbilities(user, ActivationMethod.FALL);
        }
    }

    @EventHandler
    public void on(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        PlayerAbilityUser user = userService.getAbilityPlayer(player);
        if (user == null) return;
        RayCollider rayCollider = new RayCollider(player, 5);
        Optional<Entity> entityOptional = rayCollider.getEntity(entity -> entity instanceof LivingEntity && entity != player);
        Optional<Map.Entry<Block, BlockFace>> blockOptional = rayCollider.getFirstBlock(false, true);
        if (entityOptional.isPresent()) {
            if (!isActivate(abilitySequenceService.registerAction(user, SequenceAction.LEFT_CLICK_ENTITY))) {
                handleAbilities(user, ActivationMethod.LEFT_CLICK_ENTITY);
            }
        } else if (blockOptional.isPresent()) {
            if (!isActivate(abilitySequenceService.registerAction(user, SequenceAction.LEFT_CLICK_BLOCK))) {
                handleAbilities(user, ActivationMethod.LEFT_CLICK_BLOCK);
            }
        } else {
            if (!isActivate(abilitySequenceService.registerAction(user, SequenceAction.LEFT_CLICK))) {
                handleAbilities(user, ActivationMethod.LEFT_CLICK);
            }
        }
    }

    @EventHandler
    public void on(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        AbilityUser user = userService.getAbilityPlayer(player);
        if (user == null) return;
        if (event.getHand() == EquipmentSlot.HAND) {
            Action action = event.getAction();
            if (action == Action.RIGHT_CLICK_AIR) {
                if (!isActivate(abilitySequenceService.registerAction(user, SequenceAction.RIGHT_CLICK))) {
                    handleAbilities(user, ActivationMethod.RIGHT_CLICK);
                }
            } else if (action == Action.RIGHT_CLICK_BLOCK) {
                if (!isActivate(abilitySequenceService.registerAction(user, SequenceAction.RIGHT_CLICK_BLOCK))) {
                    handleAbilities(user, ActivationMethod.RIGHT_CLICK_BLOCK);
                }
            }
        }
    }

    @EventHandler
    public void on(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        AbilityUser user = userService.getAbilityPlayer(player);
        if (user == null || event.getHand() != EquipmentSlot.HAND) return;
        if (!isActivate(abilitySequenceService.registerAction(user, SequenceAction.RIGHT_CLICK_ENTITY))) {
            handleAbilities(user, ActivationMethod.RIGHT_CLICK_ENTITY);
        }
    }

    @EventHandler
    public void on(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        AbilityUser user = userService.getAbilityPlayer(player);
        if (user == null) return;
        if (!isActivate(abilitySequenceService.registerAction(user, SequenceAction.HAND_SWAP))) {
            handleAbilities(user, ActivationMethod.HAND_SWAP);
        }
    }

    private boolean isActivate(ActivateResult result) {
        return result == ActivateResult.ACTIVATE;
    }

    public AbilityUserService userService() {
        return userService;
    }

    public AbilityService abilityService() {
        return abilityService;
    }

    public AbilityInstanceService abilityInstanceService() {
        return abilityInstanceService;
    }

    public AbilitySequenceService abilitySequenceService() {
        return abilitySequenceService;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (AbilityHandler) obj;
        return Objects.equals(this.userService, that.userService) &&
                Objects.equals(this.abilityService, that.abilityService) &&
                Objects.equals(this.abilityInstanceService, that.abilityInstanceService) &&
                Objects.equals(this.abilitySequenceService, that.abilitySequenceService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userService, abilityService, abilityInstanceService, abilitySequenceService);
    }

    @Override
    public String toString() {
        return "AbilityHandler[" +
                "userService=" + userService + ", " +
                "abilityService=" + abilityService + ", " +
                "abilityInstanceService=" + abilityInstanceService + ", " +
                "abilitySequenceService=" + abilitySequenceService + ']';
    }

}
