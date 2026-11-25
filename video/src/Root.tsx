import { Composition } from "remotion";
import { Professor, professorFrames, FPS } from "./Professor";
import { Reels, REELS_FRAMES } from "./Reels";

export const Root = () => (
  <>
    <Composition
      id="Professor"
      component={Professor}
      durationInFrames={professorFrames()}
      fps={FPS}
      width={1920}
      height={1080}
    />
    <Composition id="Reels" component={Reels} durationInFrames={REELS_FRAMES} fps={FPS} width={1080} height={1920} />
  </>
);
