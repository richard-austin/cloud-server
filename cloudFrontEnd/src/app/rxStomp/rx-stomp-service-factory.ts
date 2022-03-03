
import { myRxStompConfig } from './my-rx-stomp.config';
import { RxStompService } from './rx-stomp-service.service';

export function rxStompServiceFactory() {
  const rxStomp = new RxStompService();
  rxStomp.configure(myRxStompConfig);
  rxStomp.activate();
  return rxStomp;
}
