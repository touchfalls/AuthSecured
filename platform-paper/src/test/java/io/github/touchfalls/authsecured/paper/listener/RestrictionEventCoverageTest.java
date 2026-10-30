package io.github.touchfalls.authsecured.paper.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** These events extend a handled parent but have their own handler list, so each needs its own handler. */
class RestrictionEventCoverageTest {

    @Test
    void eventsWithTheirOwnHandlerListAreHandled() {
        Set<Class<?>> handled = new HashSet<>();
        for (Class<?> listener : new Class<?>[] {PlayerAuthRestrictionListener.class, PlayerInteractionListener.class}) {
            for (Method method : listener.getDeclaredMethods()) {
                if (method.isAnnotationPresent(EventHandler.class)) handled.add(method.getParameterTypes()[0]);
            }
        }
        for (Class<?> event : Set.of(PlayerPortalEvent.class, InventoryDragEvent.class,
                PlayerInteractAtEntityEvent.class, PlayerArmorStandManipulateEvent.class)) {
            assertTrue(handled.contains(event), event.getSimpleName() + " is not handled");
        }
    }
}
