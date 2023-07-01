package com.shadowcs.observer;

import lombok.Data;
import picocli.CommandLine;

import java.nio.file.Path;

@Data
public final class ReplaySettings {

    @CommandLine.Option(names = {"-pa", "--path"}, description = "Path to a single SC2 replay or directory with replay files.")
    private Path replayPath;
    @CommandLine.Option(names = {"-da", "--data"}, description = "The data version of StarCraft II to use.", defaultValue = "B89B5D6FA7CBF6452E721311BFBC6CB2")
    private String data;
    @CommandLine.Option(names = {"-sp", "--speed"}, description = "Replay Speed.", defaultValue = "2")
    private Float speed;
    @CommandLine.Option(names = {"-de", "--delay"}, description = "Delay after game in ms.", defaultValue = "3000")
    private Integer delay;
    @CommandLine.Option(names = {"-ro", "--rotate"}, description = "How long between rotating between displaying different production values (ex: Income vs AMP vs Production).", defaultValue = "10000")
    private Integer rotate;
    @CommandLine.Option(names = {"-to", "--toggle"}, description = "Path to a single SC2 replay or directory with replay files.", defaultValue = "true")
    private Boolean production;

    // TODO: do we eventually want to loop through overlay data?
}
