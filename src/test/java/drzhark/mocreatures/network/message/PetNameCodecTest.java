package drzhark.mocreatures.network.message;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PetNameCodecTest {
    @Test
    public void roundTripsLegacyFormatAcrossBufferTypes()
    {
        String[] names = { "", "Mr. Whiskers 2", "Grüße", "猫咪", "Лошадь", "馬🐴" };
        for (String name : names)
        {
            for (boolean direct : new boolean[] { false, true })
            {
                ByteBuf buffer = direct ? Unpooled.directBuffer() : Unpooled.buffer();
                try
                {
                    buffer.writeByte(99);
                    new MoCMessageUpdatePetName(1234, name).toBytes(buffer);
                    buffer.readByte();
                    assertEquals(name.getBytes(StandardCharsets.UTF_8).length, buffer.getInt(buffer.readerIndex()));
                    MoCMessageUpdatePetName decoded = new MoCMessageUpdatePetName();
                    decoded.fromBytes(buffer.slice());
                    assertEquals(name, decoded.name);
                    assertEquals(1234, decoded.entityId);
                }
                finally
                {
                    buffer.release();
                }
            }
        }
    }

    @Test
    public void limitsNamesWithoutSplittingSupplementaryCharacters()
    {
        String name = "";
        for (int i = 0; i < 40; i++) name += "🐴";
        ByteBuf buffer = Unpooled.buffer();
        try
        {
            new MoCMessageUpdatePetName(7, name).toBytes(buffer);
            MoCMessageUpdatePetName decoded = new MoCMessageUpdatePetName();
            decoded.fromBytes(buffer);
            assertEquals(32, decoded.name.codePointCount(0, decoded.name.length()));
            assertEquals(name.substring(0, 64), decoded.name);
        }
        finally
        {
            buffer.release();
        }
    }

    @Test
    public void rejectsMalformedLengthsAndEncoding()
    {
        for (int length : new int[] { -1, Integer.MAX_VALUE, 129, 4 })
        {
            ByteBuf buffer = Unpooled.buffer().writeInt(length).writeInt(123);
            try
            {
                assertThrows(DecoderException.class, () -> new MoCMessageUpdatePetName().fromBytes(buffer));
            }
            finally
            {
                buffer.release();
            }
        }
        byte[][] invalidNames = { { (byte) 0xC3, 0x28 }, new byte[33] };
        for (byte[] name : invalidNames)
        {
            ByteBuf buffer = Unpooled.buffer().writeInt(name.length).writeBytes(name).writeInt(123);
            try
            {
                assertThrows(DecoderException.class, () -> new MoCMessageUpdatePetName().fromBytes(buffer));
            }
            finally
            {
                buffer.release();
            }
        }
        ByteBuf truncated = Unpooled.buffer().writeInt(0);
        try
        {
            assertThrows(DecoderException.class, () -> new MoCMessageUpdatePetName().fromBytes(truncated));
        }
        finally
        {
            truncated.release();
        }
    }
}
