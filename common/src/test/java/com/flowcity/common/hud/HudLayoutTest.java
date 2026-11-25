package com.flowcity.common.hud;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.Protocol;
import com.flowcity.common.protocol.packets.HudConfigPacket;
import com.flowcity.common.protocol.packets.HudSavePacket;
import com.flowcity.common.protocol.packets.MoneyStatePacket;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class HudLayoutTest {

    @Test
    void defaultsRoundTrip() {
        HudLayout d = HudLayout.defaults();
        HudLayout back = HudLayout.parse(d.encode());
        assertEquals(d.encode(), back.encode());
        for (HudElement e : HudElement.values()) {
            assertNotNull(back.get(e), e.key);
        }
    }

    @Test
    void defaultsHaveNothingInTopLeftCorner() {
        HudLayout d = HudLayout.defaults();
        for (HudElement e : HudElement.values()) {
            assertEquals(HudElementConfig.ANCHOR_TOP_RIGHT, d.get(e).anchor, e.key);
        }
    }

    @Test
    void defaultRowDoesNotOverlap() {
        HudLayout d = HudLayout.defaults();
        HudElement[] row = {HudElement.ID, HudElement.NAME, HudElement.RANK, HudElement.PLAYTIME};
        for (int i = 0; i + 1 < row.length; i++) {
            // anchored right: larger dx = further left; neighbour must start after this one ends
            assertTrue(d.get(row[i]).dx >= d.get(row[i + 1]).dx + row[i + 1].width, row[i].key);
        }
    }

    @Test
    void clampsOutOfRangeNumbers() {
        HudLayout l = HudLayout.parse("v1|9|999|id,1,9,999999,-999999,5,999,-5");
        HudElementConfig c = l.get(HudElement.ID);
        assertEquals(HudElementConfig.ANCHOR_BOTTOM_RIGHT, c.anchor);
        assertEquals(HudElementConfig.MAX_OFFSET, c.dx);
        assertEquals(-HudElementConfig.MAX_OFFSET, c.dy);
        assertEquals(HudElementConfig.MIN_SCALE, c.scalePercent);
        assertEquals(255, c.alpha);
        assertEquals(-1, c.accent);
        assertEquals(HudLayout.MAX_UI_SCALE, l.uiScalePercent);
        assertEquals(HudLayout.FONT_COUNT - 1, l.font);
    }

    @Test
    void missingElementsGetDefaults() {
        HudLayout l = HudLayout.parse("v1|0|100|");
        assertEquals(HudLayout.defaults().encode(), l.encode());
    }

    @Test
    void rejectsGarbage() {
        String[] bad = {
                null, "", "v2|0|100|", "v1|0|100", "v1|0|100|id,1,1,1,1,1,1",
                "v1|0|100|nope,1,1,1,1,100,100,-1", "v1|0|100|id,1,1,1,1,100,100,-1;id,1,1,1,1,100,100,-1",
                "v1|x|100|", "v1|0|100|id,a,1,1,1,100,100,-1", "v1|0|99999999999|",
                new String(new char[HudLayout.MAX_ENCODED_LENGTH + 1]).replace('\0', 'a')
        };
        for (String s : bad) {
            assertThrows(IllegalArgumentException.class, () -> HudLayout.parse(s), String.valueOf(s));
        }
    }

    @Test
    void packetsRoundTrip() throws Exception {
        String layout = HudLayout.defaults().encode();
        FlowPacketReader r = FlowPacketReader.open(new HudSavePacket(2, "Mine", layout).encode(),
                Protocol.MAX_SERVERBOUND_BYTES);
        HudSavePacket save = HudSavePacket.decode(r);
        assertEquals(2, save.slot);
        assertEquals("Mine", save.name);
        assertEquals(layout, save.layout);

        r = FlowPacketReader.open(new HudConfigPacket(2,
                Collections.singletonList(new HudConfigPacket.Preset(2, "Mine", layout))).encode(),
                Protocol.MAX_CLIENTBOUND_BYTES);
        HudConfigPacket cfg = HudConfigPacket.decode(r);
        assertEquals(2, cfg.activeSlot);
        assertEquals(1, cfg.presets.size());
        assertEquals(layout, cfg.presets.get(0).layout);

        r = FlowPacketReader.open(new MoneyStatePacket(500000, 2500000, "$", 0).encode(),
                Protocol.MAX_CLIENTBOUND_BYTES);
        MoneyStatePacket m = MoneyStatePacket.decode(r);
        assertEquals(500000, m.cashCents);
        assertEquals(2500000, m.bankCents);
        assertEquals("$", m.symbol);
    }
}
