package de.TeutonStudio.TeutonLib.integration.computer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChannelBankTest {
    @Test void outputRetainsValueAndPrivacyThroughRename() {
        var bank=new ChannelBank(); bank.create("throttle","output","");
        assertFalse(bank.get("throttle").published()); bank.set("throttle",13); bank.published("throttle",true);
        bank.rename("throttle","engine"); assertEquals(13,bank.get("engine").value()); assertTrue(bank.get("engine").published());
        assertThrows(IllegalArgumentException.class,()->bank.get("throttle"));
    }
    @Test void invalidChangesDoNotDestroyDefinition() {
        var bank=new ChannelBank(); bank.create("a","output",""); bank.set("a",8); bank.create("b","output","");
        assertThrows(IllegalArgumentException.class,()->bank.rename("a","b"));
        assertThrows(IllegalArgumentException.class,()->bank.rename("a","bad/name"));
        assertThrows(IllegalArgumentException.class,()->bank.set("a",16));
        assertThrows(IllegalArgumentException.class,()->bank.set("a",-1));
        assertEquals(8,bank.get("a").value()); assertEquals(2,bank.entries().size());
    }
    @Test void inputHasExplicitSideAndCannotBeWritten() {
        var bank=new ChannelBank();
        assertThrows(IllegalArgumentException.class,()->bank.create("a","input",""));
        bank.create("a","input","left"); assertEquals("left",bank.get("a").side());
        assertThrows(IllegalArgumentException.class,()->bank.set("a",3));
    }
    @Test void definitionsCanBeRestoredWithoutChangingPrivacyOrOutput() {
        var original=new ChannelBank(); original.create("a","output",""); original.set("a",15); original.published("a",true);
        original.create("b","input","top"); var copy=new ChannelBank(); original.entries().forEach(copy::restore);
        assertEquals(original.entries(),copy.entries());
    }
    @Test void limitsAndReservedNamesAreEnforced() {
        var bank=new ChannelBank(); assertThrows(IllegalArgumentException.class,()->bank.create("world","output",""));
        for(int i=0;i<64;i++) bank.create("a"+i,"output","");
        assertThrows(IllegalArgumentException.class,()->bank.create("extra","output",""));
        bank.remove("a0"); bank.create("extra","output",""); assertEquals(64,bank.entries().size());
    }
}
