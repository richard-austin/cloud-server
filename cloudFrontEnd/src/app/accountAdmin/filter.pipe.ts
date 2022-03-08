import { Pipe, PipeTransform } from '@angular/core';
import {Account} from "../shared/utils.service";

@Pipe({
  name: 'filter'
})
export class FilterPipe implements PipeTransform {
  transform(value: Account[], ...args: string[]): Account[] {
    if(args[0] === "")
      return value;
    return value.filter(account => {
      return account.productId?.toLowerCase().includes(args[0]?.toLowerCase())
        || account.userName?.toLowerCase().includes(args[0]?.toLowerCase());
    });
  }
}
