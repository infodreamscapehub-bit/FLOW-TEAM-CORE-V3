package com.flowcity.common.protocol.packets;

import com.flowcity.common.protocol.FlowPacketReader;
import com.flowcity.common.protocol.FlowPacketWriter;
import com.flowcity.common.protocol.PacketIds;
import com.flowcity.common.protocol.ProtocolException;

/**
 * Clientbound: the receiver's cash and bank balance for the HUD. Amounts are sent in minor
 * units (cents) as longs so no floating point error ever reaches the screen.
 */
public final class MoneyStatePacket {

    public final long cashCents;
    public final long bankCents;
    public final String symbol;
    /** Decimals to show (0..2). */
    public final int decimals;

    public MoneyStatePacket(long cashCents, long bankCents, String symbol, int decimals) {
        this.cashCents = cashCents;
        this.bankCents = bankCents;
        this.symbol = symbol;
        this.decimals = decimals;
    }

    public byte[] encode() {
        return FlowPacketWriter.create(PacketIds.CB_MONEY_STATE)
                .writeLong(cashCents)
                .writeLong(bankCents)
                .writeString(symbol)
                .writeVarInt(decimals)
                .toByteArray();
    }

    public static MoneyStatePacket decode(FlowPacketReader r) throws ProtocolException {
        long cash = r.readLong();
        long bank = r.readLong();
        String symbol = r.readString(8);
        int decimals = r.readVarInt();
        if (decimals < 0 || decimals > 2) {
            throw new ProtocolException("decimals out of range");
        }
        r.expectEnd();
        return new MoneyStatePacket(cash, bank, symbol, decimals);
    }
}
