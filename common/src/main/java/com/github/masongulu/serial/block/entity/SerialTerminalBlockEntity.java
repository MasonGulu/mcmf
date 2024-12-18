package com.github.masongulu.serial.block.entity;

import com.github.masongulu.serial.SerialType;
import com.github.masongulu.serial.TerminalFont;
import com.github.masongulu.serial.screen.SerialTerminalMenu;
import com.github.masongulu.serial.screen.TerminalBuffer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static com.github.masongulu.ModBlockEntities.SERIAL_TERMINAL_BLOCK_ENTITY;

public class SerialTerminalBlockEntity extends SerialPeerBlockEntity implements MenuProvider {
    // Terminal Width = 240
    // Terminal Height = 180
    public final static int TERM_WIDTH = 240;
    public final static int TERM_HEIGHT = 180;
    public int width;
    public int height;
    public int dataSize;
    public TerminalFont font;
    public boolean echo = true;
    private final ContainerData data = new SerialTerminalContainerData(this);
    public TerminalBuffer buffer = new TerminalBuffer();
    private boolean argumentMode = false;
    private StringBuilder lineBuffer = new StringBuilder();

    public static final String ARGUMENT_MODE_SEQUENCE = "\0\n\nARGS?> ";
    private int argumentModeIndex = 0;

    public void setFont(TerminalFont font) {
        this.font = font;
        width = TERM_WIDTH / (font.width + font.hpad);
        height = TERM_HEIGHT / font.height;
        dataSize = width * height;
        buffer.setResolution(width, height);
    }
    public void nextFont() {
        setFont(font.next());
    }

    public SerialTerminalBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(SERIAL_TERMINAL_BLOCK_ENTITY.get(), blockPos, blockState);
        setFont(TerminalFont.TIMES9K);
    }
    @Override
    protected Component getDefaultName() {
        return new TextComponent("Serial Terminal");
    }

    @Override
    protected AbstractContainerMenu createMenu(int i, Inventory inventory) {
        return new SerialTerminalMenu(i, inventory, this, this.data);
    }


    @Override
    public void write(char ch) {
        write(ch, SerialType.STDIN);
    }

    @Override
    public void write(char ch, SerialType type) {
        if (ch == 0x07) {
            // TODO bell
        }
        if (!argumentMode && ch == ARGUMENT_MODE_SEQUENCE.charAt(argumentModeIndex)) {
            argumentModeIndex++;
            if (argumentModeIndex == ARGUMENT_MODE_SEQUENCE.length()) {
                argumentMode = true;
                argumentModeIndex = 0;
            }
        } else {
            argumentModeIndex = 0;
        }
        buffer.write(ch);
    }

    @Override
    public int getContainerSize() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getItem(int i) {
        return null;
    }

    @Override
    public ItemStack removeItem(int i, int j) {
        return null;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int i) {
        return null;
    }

    @Override
    public void setItem(int i, ItemStack itemStack) {

    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {

    }

    ArgumentParseState parseState;
    private enum ArgumentParseState {
        ARGUMENT,
        QUOTE_SEARCHING,
        WHITESPACE_SKIPPING
    }
    private void setParseState(ArgumentParseState state) {
        if (parseState == ArgumentParseState.WHITESPACE_SKIPPING && state != parseState) {
            peer.write('\0', SerialType.ARGUMENT_SPACER);
        }
        parseState = state;
    }
    private void parseArguments(char ch) {
        switch (parseState) {
            case ARGUMENT -> {
                if (ch == ' ') {
                    setParseState(ArgumentParseState.WHITESPACE_SKIPPING);
                } else if (ch == '"') {
                    setParseState(ArgumentParseState.QUOTE_SEARCHING);
                } else {
                    peer.write(ch, SerialType.ARGUMENT);
                }
            }
            case QUOTE_SEARCHING -> {
                if (ch == '"') {
                    setParseState(ArgumentParseState.WHITESPACE_SKIPPING);
                } else {
                    peer.write(ch, SerialType.ARGUMENT);
                }
            }
            case WHITESPACE_SKIPPING -> {
                if (ch == '"') {
                    setParseState(ArgumentParseState.QUOTE_SEARCHING);
                } else if (ch != ' ') {
                    setParseState(ArgumentParseState.ARGUMENT);
                    peer.write(ch, SerialType.ARGUMENT);
                }
            }
        }
    }
    private void onLineEnd(String line) {
        if (argumentMode && peer != null) {
            parseState = ArgumentParseState.ARGUMENT;
            for (char ch : line.toCharArray()) {
                parseArguments(ch);
            }
            peer.write('\0', SerialType.ARGUMENT_END);
        }
        argumentMode = false;
    }

    public void keyPress(char i) {
        if (echo || argumentMode) write(i);
        if (peer != null && !argumentMode) peer.write(i);
        if (i == '\n') {
            onLineEnd(lineBuffer.toString());
            lineBuffer = new StringBuilder();
        } else if (i == 127) { // backspace??
            if (!lineBuffer.isEmpty()) lineBuffer.deleteCharAt(lineBuffer.length() - 1);
            if (argumentMode) buffer.write('*'); // backspace indication
        } else {
            lineBuffer.append(i);
        }
    }
}
