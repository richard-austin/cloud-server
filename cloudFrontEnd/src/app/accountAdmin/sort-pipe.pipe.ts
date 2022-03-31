import { Pipe, PipeTransform } from '@angular/core';
import {Account} from "../shared/utils.service";

@Pipe({
  name: 'sort'
})
export class SortPipe implements PipeTransform {

  transform(value: Account[], ...args: string[]): Account[] {
    return value.sort(((a:Account, b:Account) => { // @ts-ignore
      return a[args[0]] > b[args[0]] ? 1 :  a[args[0]] < b[args[0]] ? -1 : 0;}))
  }
}
