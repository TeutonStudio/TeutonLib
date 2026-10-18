package de.TeutonStudio.TeutonLib.integration;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CsoCommandTreeTest {
    @Test void onlyAdministratorsCanChangeState() throws Exception {
        var dispatcher = new CommandDispatcher<Integer>();
        var changes = new ArrayList<Boolean>();
        CsoCommandTree.register(dispatcher, level -> level >= 2, level -> 1,
                (level, enabled) -> { changes.add(enabled); return 1; });
        for (int level : List.of(0, 1)) {
            assertEquals(1, dispatcher.execute("teutonlib cso status", level));
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("teutonlib cso enable", level));
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("teutonlib cso disable", level));
        }
        assertTrue(changes.isEmpty());
        assertEquals(1, dispatcher.execute("teutonlib cso enable", 2));
        assertEquals(1, dispatcher.execute("teutonlib cso disable", 4));
        assertEquals(List.of(true, false), changes);
    }
    @Test void invalidSubcommandsNeverChangeState() {
        var dispatcher = new CommandDispatcher<Integer>();
        CsoCommandTree.register(dispatcher, level -> level >= 2, level -> 1,
                (level, enabled) -> { fail("Invalid command changed state"); return 0; });
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("teutonlib cso toggle", 4));
    }
}
