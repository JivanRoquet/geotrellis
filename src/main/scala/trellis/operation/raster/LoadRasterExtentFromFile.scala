package trellis.operation

import trellis.RasterExtent
import trellis.process._
import trellis.process._

/**
  * Load the [[trellis.geoattrs.RasterExtent]] from the raster in the specified file.
  */
case class LoadRasterExtentFromFile(path:String) extends Op1(path)({
  (path) => {
    val i = path.lastIndexOf(".")
    val jsonPath = (if (i == -1) path else path.substring(0, i)) + ".json"
    val layer = RasterLayer.fromPath(jsonPath)
    Result(layer.rasterExtent)
  }
})

case class LoadRasterExtent(nme:Op[String]) extends Op[RasterExtent] {
  def _run(context:Context) = runAsync(List(nme, context))
  val nextSteps:Steps = {
    case (name:String) :: (context:Context) :: Nil => {
      Result(context.getRasterExtentByName(name))
    }
  }
}

//object LoadRasterExtentFromFile {
//  def apply(path:String) = LoadRasterExtentFromFile(path)
//}
