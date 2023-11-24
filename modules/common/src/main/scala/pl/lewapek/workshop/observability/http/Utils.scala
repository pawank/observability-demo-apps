package pl.lewapek.workshop.observability.http

import pl.lewapek.workshop.observability.AppError
import pl.lewapek.workshop.observability.Bootstrap.SttpBackendType
import sttp.client3.{Empty, Request, RequestT}
import zio.http.Body
import zio.json.*
import zio.json.ast.Json
import zio.{IO, ZIO}

extension (body: Body)
  def jsonAs[A: JsonDecoder]: IO[AppError, A] =
    for
      string <- body.asString.mapError(throwable => AppError.internal("Problem getting body as string"))
      a <- ZIO
        .fromEither(string.fromJson[A])
        .mapError(string => AppError.internal(s"Couldn't convert json string to class"))
    yield a
end extension

type JsonRequestT = RequestT[Empty, Either[String, Json], Any]
type JsonRequest  = Request[Either[String, Json], Any]
trait SttpUtils(backend: SttpBackendType, request: JsonRequestT):
  def send[A: JsonDecoder](headers: Map[String, String], transform: JsonRequestT => JsonRequest): IO[AppError, A] =
    backend
      .send(transform(request.headers(headers)))
      .mapBoth(
        AppError.internal("Error sending products request", _),
        _.body.left
          .map(msg => AppError.internal(s"Couldn't read products response: $msg"))
          .flatMap(
            _.as[A].left
              .map(msg => AppError.internal(s"Couldn't convert json to response model: $msg"))
          )
      )
      .absolve
  end send
end SttpUtils