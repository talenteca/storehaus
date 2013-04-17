/*
 * Copyright 2013 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.storehaus

import com.twitter.util.{ Duration, Future, Return, Throw, Timer }

class RetriableReadableStore[K, V](store: ReadableStore[K, V], backoffs: Stream[Duration])(pred: Option[V] => Boolean)(implicit timer: Timer) extends ReadableStore[K, V] {

  private[this] def getWithRetry(k: K, backoffs: Stream[Duration]): Future[Option[V]] =
    store.get(k).filter(pred) transform {
      case Return(t) => Future.value(t)
      case Throw(e) =>
        if (backoffs.isEmpty) {
          Future.exception(new MissingValueException(k))
        } else {
          Future.flatten {
            timer.doLater(backoffs.head) {
              getWithRetry(k, backoffs.tail)
            }
          }
        }
    }

  override def get(k: K) = getWithRetry(k, backoffs)
}
