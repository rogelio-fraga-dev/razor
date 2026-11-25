import { Composition } from "remotion";
import { Professor, professorFrames, FPS } from "./Professor";

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
  </>
);
